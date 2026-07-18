package com.psplatform.backend.auth;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.psplatform.backend.user.User;
import com.psplatform.backend.user.PasswordChangeRequest;
import com.psplatform.backend.user.UserRepository;
import com.psplatform.backend.user.UserService;
import com.psplatform.backend.user.UsernameUpdateRequest;

@Service
public class AuthService {

	private final Map<String, AuthProvider> providers;
	private final RefreshTokenService refreshTokenService;
	private final JwtTokenService jwtTokenService;
	private final UserRepository userRepository;
	private final UserService userService;
	private final RefreshTokenRepository refreshTokenRepository;

	public AuthService(
		List<AuthProvider> providers,
		RefreshTokenService refreshTokenService,
		JwtTokenService jwtTokenService,
		UserRepository userRepository,
		UserService userService,
		RefreshTokenRepository refreshTokenRepository
	) {
		this.providers = providers.stream()
			.collect(Collectors.toMap(AuthProvider::provider, provider -> provider));
		this.refreshTokenService = refreshTokenService;
		this.jwtTokenService = jwtTokenService;
		this.userRepository = userRepository;
		this.userService = userService;
		this.refreshTokenRepository = refreshTokenRepository;
	}

	public AuthTokenPayload login(AuthLoginRequest request) {
		AuthProvider provider = providers.get(request.provider());
		if (provider == null) {
			throw new UnsupportedAuthProviderException(request.provider());
		}

		User user = provider.authenticate(request);
		TokenPair refreshToken = refreshTokenService.createForUser(user);
		String accessToken = jwtTokenService.createAccessToken(user);

		return new AuthTokenPayload(
			accessToken,
			refreshToken.refreshToken(),
			jwtTokenService.getAccessTokenExpiresInSeconds(),
			user
		);
	}

	public AuthTokenPayload refresh(String refreshToken) {
		RefreshToken token = refreshTokenService.assertValidAndGet(refreshToken);
		TokenPair newRefreshToken = refreshTokenService.rotateForRefresh(refreshToken);
		User user = userRepository.findById(token.getUserId())
			.orElseThrow(() -> new AuthException("User not found"));
		assertActive(user);

		return new AuthTokenPayload(
			jwtTokenService.createAccessToken(user),
			newRefreshToken.refreshToken(),
			jwtTokenService.getAccessTokenExpiresInSeconds(),
			user
		);
	}

	public void logout(String refreshToken) {
		refreshTokenService.revoke(refreshToken);
	}

	public User verifyAccessToken(String authorizationHeader) {
		String accessToken = resolveBearer(authorizationHeader);
		JwtClaims claims = jwtTokenService.parseAccessToken(accessToken);
		User user = userRepository.findById(claims.userId())
			.orElseThrow(() -> new AuthException("User not found"));
		assertActive(user);
		if (claims.authVersion() != user.getAuthVersion()) {
			throw new InvalidAccessTokenException("Access token has been invalidated");
		}
		return user;
	}

	public Optional<User> findUserIfAuthenticated(String authorizationHeader) {
		if (authorizationHeader == null || authorizationHeader.isBlank()) {
			return Optional.empty();
		}
		return Optional.of(verifyAccessToken(authorizationHeader));
	}

	public User updateCurrentUsername(String authorizationHeader, UsernameUpdateRequest request) {
		return userService.updateUsername(verifyAccessToken(authorizationHeader), request);
	}

	@Transactional
	public void changeCurrentPassword(String authorizationHeader, PasswordChangeRequest request) {
		User user = verifyAccessToken(authorizationHeader);
		userService.changePassword(user, request);
		refreshTokenRepository.revokeAllByUserId(user.getId());
	}

	@Transactional
	public void deleteCurrentUser(String authorizationHeader) {
		User user = verifyAccessToken(authorizationHeader);
		userRepository.deleteById(user.getId());
	}

	private String resolveBearer(String authorizationHeader) {
		if (authorizationHeader == null || authorizationHeader.isBlank()) {
			throw new InvalidAccessTokenException("Authorization header is required");
		}
		if (!authorizationHeader.startsWith("Bearer ")) {
			throw new InvalidAccessTokenException("Authorization header must start with Bearer");
		}
		return authorizationHeader.substring("Bearer ".length()).trim();
	}

	private void assertActive(User user) {
		if (user.isDeleted()) {
			throw new InvalidAccessTokenException("User not found");
		}
		if (user.isSuspended()) {
			throw new InvalidAccessTokenException("User account is suspended");
		}
	}
}
