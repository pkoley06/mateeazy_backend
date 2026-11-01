package com.matcher.matchmate_backend.security;

import com.matcher.matchmate_backend.model.entity.AppUser;
import com.matcher.matchmate_backend.model.enums.auth.AccountStatus;
import com.matcher.matchmate_backend.repository.AppUserRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtTokenProvider jwtTokenProvider;
    private final AppUserRepository userRepository;

    public JwtAuthenticationFilter(JwtTokenProvider jwtTokenProvider, AppUserRepository userRepository) {
        this.jwtTokenProvider = jwtTokenProvider;
        this.userRepository = userRepository;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {

        String jwt = extractJwtFromRequest(request);

        if( StringUtils.hasText(jwt) && jwtTokenProvider.validateToken(jwt) && !jwtTokenProvider.isAdminToken(jwt) ){
            UUID userId = jwtTokenProvider.getUserIdFromToken(jwt);
            String firebaseUid = jwtTokenProvider.getFirebaseUidFromToken(jwt);

            // Check if user is suspended or deactivated
            Optional<AppUser> userOpt = userRepository.findById(userId);
            if (userOpt.isPresent()) {
                AppUser user = userOpt.get();
                if (Boolean.TRUE.equals(user.getIsSuspended())) {
                    response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                    response.setContentType("application/json");
                    String reason = user.getSuspendedReason() != null ? user.getSuspendedReason() : "Your account has been suspended.";
                    response.getWriter().write("{\"error\":\"ACCOUNT_SUSPENDED\",\"message\":\"" + reason + "\"}");
                    return;
                }
                if (!Boolean.TRUE.equals(user.getIsActive())) {
                    // Stateless JWTs: the active flag is the revocation list. One narrow
                    // carve-out: a PENDING_DELETION user whose grace window hasn't lapsed is
                    // still let through for the one call that can undo it, so the confirm screen
                    // can actually cancel the deletion while every other route stays blocked.
                    boolean isCancelDeletionCall = "POST".equalsIgnoreCase(request.getMethod())
                            && "/api/v1/users/me/cancel-deletion".equals(request.getRequestURI());
                    boolean purgeWindowStillOpen = user.getAccountStatus() == AccountStatus.PENDING_DELETION
                            && user.getScheduledPurgeAt() != null
                            && user.getScheduledPurgeAt().isAfter(OffsetDateTime.now());

                    if (isCancelDeletionCall && purgeWindowStillOpen) {
                        setAuthentication(request, userId, firebaseUid);
                        filterChain.doFilter(request, response);
                        return;
                    }

                    response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                    response.setContentType("application/json");
                    String body = switch (user.getAccountStatus()) {
                        case DISABLED -> "{\"error\":\"ACCOUNT_DISABLED\",\"message\":\"Your account is disabled. Sign in again to re-activate it.\"}";
                        case PENDING_DELETION -> "{\"error\":\"ACCOUNT_PENDING_DELETION\",\"message\":\"Your account is scheduled for deletion.\"}";
                        default -> "{\"error\":\"ACCOUNT_DEACTIVATED\",\"message\":\"Your account has been deactivated.\"}";
                    };
                    response.getWriter().write(body);
                    return;
                }
            }

            setAuthentication(request, userId, firebaseUid);
        }

        filterChain.doFilter(request, response);
    }

    private void setAuthentication(HttpServletRequest request, UUID userId, String firebaseUid) {
        AuthenticatedUserPrincipal principal = new AuthenticatedUserPrincipal(userId, firebaseUid);
        UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
        authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }

    private String extractJwtFromRequest(HttpServletRequest request){
        String bearerToken = request.getHeader("Authorization");

        if (StringUtils.hasText(bearerToken) && bearerToken.startsWith("Bearer ")){
            return bearerToken.substring(7);
        }
        return null;
    }
}
