package com.psplatform.backend.problem;

public class DuplicateProblemTitleException extends RuntimeException {

	public DuplicateProblemTitleException(String title) {
		super("Problem title already exists: " + title);
	}

	public DuplicateProblemTitleException(String title, Throwable cause) {
		super("Problem title already exists: " + title, cause);
	}
}
