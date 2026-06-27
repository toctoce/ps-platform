package com.psplatform.backend.judge;

public class JudgeJobNotFoundException extends RuntimeException {

	public JudgeJobNotFoundException(long id) {
		super("Judge job not found: " + id);
	}
}
