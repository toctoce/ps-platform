package com.psplatform.backend.user;

import java.util.Optional;

public interface UserRepository {

	User save(String email, String username, String passwordHash, String role);

	Optional<User> findById(long id);

	Optional<User> findByEmail(String email);

	Optional<User> findByUsername(String username);

	void deleteAll();
}
