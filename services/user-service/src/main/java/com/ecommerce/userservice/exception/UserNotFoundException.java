package com.ecommerce.userservice.exception;

/**
 * User Not Found Exception
 *
 * Thrown when a user is not found in the database.
 */
public class UserNotFoundException extends RuntimeException {

    public UserNotFoundException(String message) {
        super(message);
    }

    public UserNotFoundException(String message, Throwable cause) {
        super(message, cause);
    }

    public static UserNotFoundException byAuth0Id(String auth0Id) {
        return new UserNotFoundException("User not found with Auth0 ID: " + auth0Id);
    }

    public static UserNotFoundException byId(Long id) {
        return new UserNotFoundException("User not found with ID: " + id);
    }

    public static UserNotFoundException byEmail(String email) {
        return new UserNotFoundException("User not found with email: " + email);
    }

}
