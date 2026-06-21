package com.example.user_module;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class UserModuleApplicationTests {

	@Test
	void applicationEntryPointExists() {
		assertThat(UserModuleApplication.class).isNotNull();
	}

}
