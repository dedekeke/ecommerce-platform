package com.ecommerce.userservice.exception;

/**
 * Address Not Found Exception
 *
 * Thrown when an address is not found in the database.
 */
public class AddressNotFoundException extends RuntimeException {

    public AddressNotFoundException(String message) {
        super(message);
    }

    public AddressNotFoundException(String message, Throwable cause) {
        super(message, cause);
    }

    public static AddressNotFoundException byId(Long id) {
        return new AddressNotFoundException("Address not found with ID: " + id);
    }

    public static AddressNotFoundException byIdAndUser(Long id, String auth0Id) {
        return new AddressNotFoundException(
                "Address not found with ID: " + id + " for user: " + auth0Id
        );
    }

}
