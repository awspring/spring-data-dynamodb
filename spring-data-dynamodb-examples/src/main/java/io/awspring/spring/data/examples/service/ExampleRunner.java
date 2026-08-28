/*
 * Copyright 2013-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.awspring.spring.data.examples.service;

import io.awspring.spring.data.examples.service.usecase.ExampleUseCase;
import java.util.List;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

/** Runs the independently documented examples in their Spring ordering. */
@Component
public class ExampleRunner implements CommandLineRunner {

	private final List<ExampleUseCase> useCases;

	public ExampleRunner(List<ExampleUseCase> useCases) {
		this.useCases = useCases;
	}

	@Override
	public void run(String... args) {
		int sequence = 1;
		for (ExampleUseCase useCase : useCases) {
			System.out.println();
			System.out.println("=== " + sequence++ + ". " + useCase.title() + " ===");
			useCase.run();
		}
	}
}
