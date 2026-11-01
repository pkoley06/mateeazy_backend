package com.matcher.matchmate_backend.service;


import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseToken;
import com.matcher.matchmate_backend.dto.request.TokenVerifyRequest;
import com.matcher.matchmate_backend.dto.response.AuthTokenResponse;
import com.matcher.matchmate_backend.dto.response.PreFilledUserData;
import com.matcher.matchmate_backend.model.entity.AppUser;
import com.matcher.matchmate_backend.model.enums.auth.AccountStatus;
import com.matcher.matchmate_backend.model.enums.auth.Gender;
import com.matcher.matchmate_backend.repository.AppUserRepository;
import com.matcher.matchmate_backend.security.JwtTokenProvider;

import jakarta.transaction.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;


@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    private  final FirebaseAuth firebaseAuth;
    private final AppUserRepository userRepository;
    private  final JwtTokenProvider jwtTokenProvider;

    public AuthService(
            FirebaseAuth firebaseAuth,
            AppUserRepository userRepository,
            JwtTokenProvider jwtTokenProvider
    ){
        this.firebaseAuth = firebaseAuth;
        this.userRepository = userRepository;
        this.jwtTokenProvider = jwtTokenProvider;
    }

    @Transactional
    public AuthTokenResponse verifyAndAuthenticate(TokenVerifyRequest tokenVerifyRequest){
        FirebaseToken decodedToken;

        try {
            decodedToken = firebaseAuth.verifyIdToken(tokenVerifyRequest.getIdToken());
        } catch (Exception e) {
            log.error("Firebase token verification failed: {}", e.getMessage());
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid or expired Firebase ID token");
        }

        String firebaseUid = decodedToken.getUid();
        String name = decodedToken.getName() == null ?  "New Roommate" : decodedToken.getName();
        String email = decodedToken.getEmail();
        String picture = decodedToken.getPicture();

        // Check if user already exists in PostgreSQL
        AppUser user = userRepository
                .findByFirebaseUid(firebaseUid)
                .orElseGet(() -> autoProvisionUser(firebaseUid, tokenVerifyRequest, name, picture));

        // Suspended accounts never get this far - a clear reason at sign-in time, rather than a
        // token that then silently fails on the very next API call.
        if (Boolean.TRUE.equals(user.getIsSuspended())) {
            String reason = user.getSuspendedReason() != null
                    ? user.getSuspendedReason()
                    : "Your account has been suspended by the administration. Contact support for help.";
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, reason);
        }

        // A self-disabled account comes back the moment its owner signs in again.
        if (user.getAccountStatus() == AccountStatus.DISABLED) {
            log.info("Re-activating disabled account {}", user.getId());
            user.setAccountStatus(AccountStatus.ACTIVE);
            user.setIsActive(true);
            user.setDisabledAt(null);
            user = userRepository.save(user);
        }

        // PENDING_DELETION is deliberately NOT auto-cancelled or auto-activated here. The account
        // stays isActive=false; JwtAuthenticationFilter blocks every route except
        // /users/me/cancel-deletion. The client is told via pendingDeletion/scheduledPurgeAt below
        // and shows a confirm screen instead of silently letting the user back in.

        // Generate custom MatchMate session JWT
        String accessToken = jwtTokenProvider.generateToken(user.getId(), user.getFirebaseUid());

       // use the real flag, NOT "gender/DOB are not null" (placeholders make that always true)
        boolean isProfileComplete = Boolean.TRUE.equals(user.getOnboardingCompleted());
        boolean pendingDeletion = user.getAccountStatus() == AccountStatus.PENDING_DELETION;

        return AuthTokenResponse.builder()
                .accessToken(accessToken)
                .tokenType("Bearer")
                .expiresIn(jwtTokenProvider.getExpirationMs() / 1000)
                .userId(user.getId())
                .isProfileComplete(isProfileComplete)
                .pendingDeletion(pendingDeletion)
                .scheduledPurgeAt(pendingDeletion ? user.getScheduledPurgeAt() : null)
                .preFilledUserData(PreFilledUserData.builder()
                        .fullName(user.getFullName())
                        .avatarUrl(user.getAvatarUrl())
                        .email(email)
                        .build())
                .build();
    }



    private AppUser autoProvisionUser(
            String firebaseUid,
            TokenVerifyRequest tokenVerifyRequest,
            String name,
            String picture) {
        log.info("Auto-provisioning new user with Firebase UID: {}", firebaseUid);

        AppUser newUser = AppUser.builder()
                .firebaseUid(firebaseUid)
                .primaryAuthProvider(tokenVerifyRequest.getProvider())
                .fullName(name)
                .avatarUrl(picture)
        // Placeholders: the DB columns are NOT NULL. They are replaced when the user submits the
        // onboarding form. Until then onboardingCompleted stays false (C4).
                .dateOfBirth(LocalDate.of(2000, 1, 1))
                .gender(Gender.OTHERS)
                .onboardingCompleted(false)
                .isActive(true)
                .build();

        return userRepository.save(newUser);

    }


}
