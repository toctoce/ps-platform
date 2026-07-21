package com.psplatform.backend.user;

import java.net.URI;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.psplatform.backend.experience.ExperienceService;

@RestController
@RequestMapping("/api/users")
public class UserController {

	private final UserService userService;
	private final ExperienceService experienceService;

	public UserController(UserService userService, ExperienceService experienceService) {
		this.userService = userService;
		this.experienceService = experienceService;
	}

	@PostMapping
	public ResponseEntity<UserResponse> signup(@RequestBody UserSignupRequest request) {
		User user = userService.signup(request);
		return ResponseEntity
			.created(URI.create("/api/users/" + user.getId()))
			.body(UserResponse.from(user, experienceService.findProgress(user.getId())));
	}

	@GetMapping("/{id}")
	public UserResponse findById(@PathVariable long id) {
		User user = userService.findById(id);
		return UserResponse.from(user, experienceService.findProgress(user.getId()));
	}
}
