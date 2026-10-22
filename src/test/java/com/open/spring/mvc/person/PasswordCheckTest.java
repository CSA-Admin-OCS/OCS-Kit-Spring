package com.open.spring.mvc.person;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class PasswordCheckTest {

    @Test
    public void testStrongPassword() {
        Person person = new Person();
        person.setPassword("Password1!");

        assertTrue(person.checkPassword());
    }

    @Test
    public void testNoUppercase() {
        Person person = new Person();
        person.setPassword("password1!");

        assertFalse(person.checkPassword());
    }

    @Test
    public void testNoLowercase() {
        Person person = new Person();
        person.setPassword("PASSWORD1!");

        assertFalse(person.checkPassword());
    }

    @Test
    public void testNoNumber() {
        Person person = new Person();
        person.setPassword("Password!");

        assertFalse(person.checkPassword());
    }

    @Test
    public void testNoSpecialCharacter() {
        Person person = new Person();
        person.setPassword("Password1");

        assertFalse(person.checkPassword());
    }

    @Test
    public void testTooShort() {
        Person person = new Person();
        person.setPassword("Pa1!");

        assertFalse(person.checkPassword());
    }

    // The same rule, as JPA applies it on save through the transient newPassword field.
    @Test
    public void testJpaValidatorRejectsWeakNewPassword() {
        try (jakarta.validation.ValidatorFactory factory = jakarta.validation.Validation.buildDefaultValidatorFactory()) {
            jakarta.validation.Validator validator = factory.getValidator();
            Person person = new Person();
            person.setNewPassword("weakpass");
            assertTrue(validator.validateProperty(person, "newPassword").stream()
                    .anyMatch(v -> v.getMessage().equals(Person.PASSWORD_RULE)));
            person.setNewPassword("Password1!");
            assertTrue(validator.validateProperty(person, "newPassword").isEmpty());
            person.setNewPassword(null); // unchanged password: nothing to check
            assertTrue(validator.validateProperty(person, "newPassword").isEmpty());
        }
    }
}
