/*
 * Copyright 2014-2022 the original author or authors.
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

package org.springframework.restdocs.payload;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link JsonContentHandler}.
 *
 * @author Andy Wilkinson
 * @author Mathias Düsterhöft
 */
public class JsonContentHandlerTests {

	@Rule
	public ExpectedException thrown = ExpectedException.none();

	@Test
	public void typeForFieldWithNullValueMustMatch() {
		this.thrown.expect(FieldTypesDoNotMatchException.class);
		FieldDescriptor descriptor = new FieldDescriptor("a").type(JsonFieldType.STRING);
		new JsonContentHandler("{\"a\": null}".getBytes(), Arrays.asList(descriptor)).resolveFieldType(descriptor);
	}

	@Test
	public void typeForFieldWithNotNullAndThenNullValueMustMatch() {
		this.thrown.expect(FieldTypesDoNotMatchException.class);
		FieldDescriptor descriptor = new FieldDescriptor("a[].id").type(JsonFieldType.STRING);
		new JsonContentHandler("{\"a\":[{\"id\":1},{\"id\":null}]}".getBytes(), Arrays.asList(descriptor))
				.resolveFieldType(descriptor);
	}

	@Test
	public void typeForFieldWithNullAndThenNotNullValueMustMatch() {
		this.thrown.expect(FieldTypesDoNotMatchException.class);
		FieldDescriptor descriptor = new FieldDescriptor("a.[].id").type(JsonFieldType.STRING);
		new JsonContentHandler("{\"a\":[{\"id\":null},{\"id\":1}]}".getBytes(), Arrays.asList(descriptor))
				.resolveFieldType(descriptor);
	}

	@Test
	public void typeForOptionalFieldWithNumberAndThenNullValueIsNumber() {
		FieldDescriptor descriptor = new FieldDescriptor("a[].id").optional();
		Object fieldType = new JsonContentHandler("{\"a\":[{\"id\":1},{\"id\":null}]}\"".getBytes(),
				Arrays.asList(descriptor)).resolveFieldType(descriptor);
		assertThat((JsonFieldType) fieldType).isEqualTo(JsonFieldType.NUMBER);
	}

	@Test
	public void typeForOptionalFieldWithNullAndThenNumberIsNumber() {
		FieldDescriptor descriptor = new FieldDescriptor("a[].id").optional();
		Object fieldType = new JsonContentHandler("{\"a\":[{\"id\":null},{\"id\":1}]}".getBytes(),
				Arrays.asList(descriptor)).resolveFieldType(descriptor);
		assertThat((JsonFieldType) fieldType).isEqualTo(JsonFieldType.NUMBER);
	}

	@Test
	public void typeForFieldWithNumberAndThenNullValueIsVaries() {
		FieldDescriptor descriptor = new FieldDescriptor("a[].id");
		Object fieldType = new JsonContentHandler("{\"a\":[{\"id\":1},{\"id\":null}]}\"".getBytes(),
				Arrays.asList(descriptor)).resolveFieldType(descriptor);
		assertThat((JsonFieldType) fieldType).isEqualTo(JsonFieldType.VARIES);
	}

	@Test
	public void typeForFieldWithNullAndThenNumberIsVaries() {
		FieldDescriptor descriptor = new FieldDescriptor("a[].id");
		Object fieldType = new JsonContentHandler("{\"a\":[{\"id\":null},{\"id\":1}]}".getBytes(),
				Arrays.asList(descriptor)).resolveFieldType(descriptor);
		assertThat((JsonFieldType) fieldType).isEqualTo(JsonFieldType.VARIES);
	}

	@Test
	public void typeForOptionalFieldWithNullValueCanBeProvidedExplicitly() {
		FieldDescriptor descriptor = new FieldDescriptor("a").type(JsonFieldType.STRING).optional();
		Object fieldType = new JsonContentHandler("{\"a\": null}".getBytes(), Arrays.asList(descriptor))
				.resolveFieldType(descriptor);
		assertThat((JsonFieldType) fieldType).isEqualTo(JsonFieldType.STRING);
	}

	@Test
	public void typeForFieldWithSometimesPresentOptionalAncestorCanBeProvidedExplicitly() {
		FieldDescriptor descriptor = new FieldDescriptor("a.[].b.c").type(JsonFieldType.NUMBER);
		FieldDescriptor ancestor = new FieldDescriptor("a.[].b").optional();
		Object fieldType = new JsonContentHandler("{\"a\":[ { \"d\": 4}, {\"b\":{\"c\":5}, \"d\": 4}]}".getBytes(),
				Arrays.asList(descriptor, ancestor)).resolveFieldType(descriptor);
		assertThat((JsonFieldType) fieldType).isEqualTo(JsonFieldType.NUMBER);
	}

	@Test
	public void failsFastWithNonJsonContent() {
		this.thrown.expect(PayloadHandlingException.class);
		new JsonContentHandler("Non-JSON content".getBytes(), Collections.emptyList());
	}

	@Test
	public void describedFieldThatIsNotPresentIsConsideredMissing() {
		List<FieldDescriptor> descriptors = Arrays.asList(new FieldDescriptor("a"), new FieldDescriptor("b"),
				new FieldDescriptor("c"));
		List<FieldDescriptor> missingFields = new JsonContentHandler("{\"a\": \"alpha\", \"b\":\"bravo\"}".getBytes(),
				descriptors).findMissingFields();
		assertThat(missingFields.size()).isEqualTo(1);
		assertThat(missingFields.get(0).getPath()).isEqualTo("c");
	}

	@Test
	public void describedOptionalFieldThatIsNotPresentIsNotConsideredMissing() {
		List<FieldDescriptor> descriptors = Arrays.asList(new FieldDescriptor("a"), new FieldDescriptor("b"),
				new FieldDescriptor("c").optional());
		List<FieldDescriptor> missingFields = new JsonContentHandler("{\"a\": \"alpha\", \"b\":\"bravo\"}".getBytes(),
				descriptors).findMissingFields();
		assertThat(missingFields.size()).isEqualTo(0);
	}

	@Test
	public void describedFieldThatIsNotPresentNestedBeneathOptionalFieldThatIsPresentIsConsideredMissing() {
		List<FieldDescriptor> descriptors = Arrays.asList(new FieldDescriptor("a").optional(), new FieldDescriptor("b"),
				new FieldDescriptor("a.c"));
		List<FieldDescriptor> missingFields = new JsonContentHandler("{\"a\":\"alpha\",\"b\":\"bravo\"}".getBytes(),
				descriptors).findMissingFields();
		assertThat(missingFields.size()).isEqualTo(1);
		assertThat(missingFields.get(0).getPath()).isEqualTo("a.c");
	}

	@Test
	public void describedFieldThatIsNotPresentNestedBeneathOptionalFieldThatIsNotPresentIsNotConsideredMissing() {
		List<FieldDescriptor> descriptors = Arrays.asList(new FieldDescriptor("a").optional(), new FieldDescriptor("b"),
				new FieldDescriptor("a.c"));
		List<FieldDescriptor> missingFields = new JsonContentHandler("{\"b\":\"bravo\"}".getBytes(), descriptors)
				.findMissingFields();
		assertThat(missingFields.size()).isEqualTo(0);
	}

	@Test
	public void describedFieldThatIsNotPresentNestedBeneathOptionalArrayThatIsEmptyIsNotConsideredMissing() {
		List<FieldDescriptor> descriptors = Arrays.asList(new FieldDescriptor("outer"),
				new FieldDescriptor("outer[]").optional(), new FieldDescriptor("outer[].inner"));
		List<FieldDescriptor> missingFields = new JsonContentHandler("{\"outer\":[]}".getBytes(), descriptors)
				.findMissingFields();
		assertThat(missingFields.size()).isEqualTo(0);
	}

	@Test
	public void describedSometimesPresentFieldThatIsChildOfSometimesPresentOptionalArrayIsNotConsideredMissing() {
		List<FieldDescriptor> descriptors = Arrays.asList(new FieldDescriptor("a.[].c").optional(),
				new FieldDescriptor("a.[].c.d"));
		List<FieldDescriptor> missingFields = new JsonContentHandler(
				"{\"a\":[ {\"b\": \"bravo\"}, {\"b\": \"bravo\", \"c\": { \"d\": \"delta\"}}]}".getBytes(), descriptors)
						.findMissingFields();
		assertThat(missingFields.size()).isEqualTo(0);
	}

	@Test
	public void describedMissingFieldThatIsChildOfNestedOptionalArrayThatIsEmptyIsNotConsideredMissing() {
		List<FieldDescriptor> descriptors = Arrays.asList(new FieldDescriptor("a.[].b").optional(),
				new FieldDescriptor("a.[].b.[]").optional(), new FieldDescriptor("a.[].b.[].c"));
		List<FieldDescriptor> missingFields = new JsonContentHandler("{\"a\":[{\"b\":[]}]}".getBytes(), descriptors)
				.findMissingFields();
		assertThat(missingFields.size()).isEqualTo(0);
	}

	@Test
	public void describedMissingFieldThatIsChildOfNestedOptionalArrayThatContainsAnObjectIsConsideredMissing() {
		List<FieldDescriptor> descriptors = Arrays.asList(new FieldDescriptor("a.[].b").optional(),
				new FieldDescriptor("a.[].b.[]").optional(), new FieldDescriptor("a.[].b.[].c"));
		List<FieldDescriptor> missingFields = new JsonContentHandler("{\"a\":[{\"b\":[{}]}]}".getBytes(), descriptors)
				.findMissingFields();
		assertThat(missingFields.size()).isEqualTo(1);
		assertThat(missingFields.get(0).getPath()).isEqualTo("a.[].b.[].c");
	}

	@Test
	public void describedMissingFieldThatIsChildOfOptionalObjectThatIsNullIsNotConsideredMissing() {
		List<FieldDescriptor> descriptors = Arrays.asList(new FieldDescriptor("a").optional(),
				new FieldDescriptor("a.b"));
		List<FieldDescriptor> missingFields = new JsonContentHandler("{\"a\":null}".getBytes(), descriptors)
				.findMissingFields();
		assertThat(missingFields.size()).isEqualTo(0);
	}

}
