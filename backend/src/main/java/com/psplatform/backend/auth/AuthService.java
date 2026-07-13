package com.psplatform.backend.auth;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.psplatform.backend.user.User;
import com.psplatform.backend.user.UserRepository;

@Service
public class AuthService {

	private final Map<String, AuthProvider> providers;
	private final RefreshTokenService refreshTokenService;
	private final JwtTokenService jwtTokenService;
	private final UserRepository userRepository;

	public AuthService(
		List<AuthProvider> providers,
		RefreshTokenService refreshTokenService,
		JwtTokenService jwtTokenService,
		UserRepository userRepository
	) {
		this.providers = providers.stream()
			.collect(Collectors.toMap(AuthProvider::provider, provider -> provider));
		this.refreshTokenService = refreshTokenService;
		this.jwtTokenService = jwtTokenService;
		this.userRepository = userRepository;
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
		if (user.isDeleted()) {
			throw new InvalidAccessTokenException("User not found");
		}
		return user;
	}

	public Optional<User> findUserIfAuthenticated(String authorizationHeader) {
		if (authorizationHeader == null || authorizationHeader.isBlank()) {
			return Optional.empty();
		}
		return Optional.of(verifyAccessToken(authorizationHeader));
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
}
