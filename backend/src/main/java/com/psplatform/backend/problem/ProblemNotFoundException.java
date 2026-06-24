package com.psplatform.backend.problem;

public class ProblemNotFoundException extends RuntimeException {

	public ProblemNotFoundException(long id) {
		super("Problem not found: " + id);
	}
}
