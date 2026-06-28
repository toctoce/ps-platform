package com.psplatform.backend.user;

import java.net.URI;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/users")
public class UserController {

	private final UserService userService;

	public UserController(UserService userService) {
		this.userService = userService;
	}

	@PostMapping
	public ResponseEntity<UserResponse> signup(@RequestBody UserSignupRequest request) {
		User user = userService.signup(request);
		return ResponseEntity
			.created(URI.create("/api/users/" + user.getId()))
			.body(UserResponse.from(user));
	}

	@GetMapping("/{id}")
	public UserResponse findById(@PathVariable long id) {
		return UserResponse.from(userService.findById(id));
	}
}
