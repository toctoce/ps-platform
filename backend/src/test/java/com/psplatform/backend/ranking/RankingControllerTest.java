package com.psplatform.backend.ranking;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import com.psplatform.backend.experience.ExperienceAwardRepository;
import com.psplatform.backend.problem.Problem;
import com.psplatform.backend.problem.ProblemCreateRequest;
import com.psplatform.backend.problem.ProblemDifficulty;
import com.psplatform.backend.problem.ProblemRepository;
import com.psplatform.backend.user.User;
import com.psplatform.backend.user.UserRepository;

@SpringBootTest
@AutoConfigureMockMvc
class RankingControllerTest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private UserRepository userRepository;

	@Autowired
	private ProblemRepository problemRepository;

	@Autowired
	private ExperienceAwardRepository experienceAwardRepository;

	@BeforeEach
	void setUp() {
		userRepository.deleteAll();
		problemRepository.deleteAll();
	}

	@Test
	void returnsTopUsersByExperienceWithSharedRanks() throws Exception {
		Problem problem = createProblem();
		User alice = createUser("alice", "alice@example.com", "USER");
		User bob = createUser("bob", "bob@example.com", "USER");
		User carol = createUser("carol", "carol@example.com", "USER");
		User admin = createUser("admin", "admin@example.com", "ADMIN");
		User deleted = createUser("deleted", "deleted@example.com", "USER");

		experienceAwardRepository.award(alice.getId(), problem.getId(), 100);
		experienceAwardRepository.award(bob.getId(), problem.getId(), 60);
		experienceAwardRepository.award(carol.getId(), problem.getId(), 60);
		experienceAwardRepository.award(admin.getId(), problem.getId(), 500);
		experienceAwardRepository.award(deleted.getId(), problem.getId(), 300);
		userRepository.deleteById(deleted.getId());

		mockMvc.perform(get("/api/rankings"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.length()").value(3))
			.andExpect(jsonPath("$[0].rank").value(1))
			.andExpect(jsonPath("$[0].username").value("alice"))
			.andExpect(jsonPath("$[0].level").value(2))
			.andExpect(jsonPath("$[0].currentLevelExperience").value(50))
			.andExpect(jsonPath("$[0].requiredExperience").value(55))
			.andExpect(jsonPath("$[1].rank").value(2))
			.andExpect(jsonPath("$[1].username").value("bob"))
			.andExpect(jsonPath("$[2].rank").value(2))
			.andExpect(jsonPath("$[2].username").value("carol"));
	}

	@Test
	void returnsAtMostTenUsers() throws Exception {
		Problem problem = createProblem();
		for (int index = 0; index < 12; index++) {
			User user = createUser("ranking-user-" + index, "ranking" + index + "@example.com", "USER");
			experienceAwardRepository.award(user.getId(), problem.getId(), 10 + index);
		}

		mockMvc.perform(get("/api/rankings"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.length()").value(10))
			.andExpect(jsonPath("$[0].currentLevelExperience").value(21))
			.andExpect(jsonPath("$[0].requiredExperience").value(50))
			.andExpect(jsonPath("$[9].currentLevelExperience").value(12));
	}

	private User createUser(String username, String email, String role) {
		return userRepository.save(email, username, "hashed-password", role);
	}

	private Problem createProblem() {
		return problemRepository.save(new ProblemCreateRequest(
			"랭킹 테스트 문제",
			"랭킹 테스트를 위한 문제",
			"정답을 출력한다.",
			"입력이 주어진다.",
			"정답을 출력한다.",
			1000,
			128,
			ProblemDifficulty.NORMAL,
			List.of(),
			List.of()
		));
	}
}
