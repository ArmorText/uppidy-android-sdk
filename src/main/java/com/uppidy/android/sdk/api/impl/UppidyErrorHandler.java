package com.uppidy.android.sdk.api.impl;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.Map;

import org.codehaus.jackson.JsonFactory;
import org.codehaus.jackson.JsonParseException;
import org.codehaus.jackson.map.ObjectMapper;
import org.codehaus.jackson.type.TypeReference;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.social.DuplicateStatusException;
import org.springframework.social.ExpiredAuthorizationException;
import org.springframework.social.InsufficientPermissionException;
import org.springframework.social.InternalServerErrorException;
import org.springframework.social.InvalidAuthorizationException;
import org.springframework.social.MissingAuthorizationException;
import org.springframework.social.NotAuthorizedException;
import org.springframework.social.OperationNotPermittedException;
import org.springframework.social.RateLimitExceededException;
import org.springframework.social.ResourceNotFoundException;
import org.springframework.social.RevokedAuthorizationException;
import org.springframework.social.UncategorizedApiException;
import org.springframework.web.client.DefaultResponseErrorHandler;

/**
 * Subclass of {@link DefaultResponseErrorHandler} that handles errors from Uppidy's API, 
 * interpreting them into appropriate exceptions.
 * 
 * @author arudnev@uppidy.com
 */
class UppidyErrorHandler extends DefaultResponseErrorHandler {

	@Override
	public void handleError(ClientHttpResponse response) throws IOException {
		Map<String, Object> errorDetails = extractErrorDetailsFromResponse(response);
		if (errorDetails == null) {
			handleUncategorizedError(response, errorDetails);
		}

		handleUppidyError(response.getStatusCode(), errorDetails);
		
		// if not otherwise handled, do default handling and wrap with UncategorizedApiException
		handleUncategorizedError(response, errorDetails);			
	}
	
	@Override 
	public boolean hasError(ClientHttpResponse response) throws IOException {
		if(super.hasError(response)) {
			return true;
		}
		// only bother checking the body for errors if we get past the default error check
		String content = readFully(response.getBody());		
		return content.startsWith("{\"error\":") || content.equals("false");
	}

	/**
	 * Examines the error data returned from Uppidy and throws the most applicable exception.
	 * @param errorDetails a Map containing a "type" and a "message" corresponding to the Uppidy API's error response structure.
	 */
	void handleUppidyError(HttpStatus statusCode, Map<String, Object> errorDetails) {
		// Can't trust the type to be useful. It's often OAuthException, even for things not OAuth-related. 
		// Can rely only on the message (which itself isn't very consistent).
		String message = (String) errorDetails.get("message");

		if (statusCode == HttpStatus.OK) {
			if (message.contains("Some of the aliases you requested do not exist")) {
				throw new ResourceNotFoundException(message);
			}
		} else if (statusCode == HttpStatus.BAD_REQUEST) {
			if (message.contains("Unknown path components")) {
				throw new ResourceNotFoundException(message);
			} else if (message.equals("An access token is required to request this resource.")) {
				throw new MissingAuthorizationException();
			} else if (message.equals("An active access token must be used to query information about the current user.")) {
				throw new MissingAuthorizationException();				
			} else if (message.startsWith("Error validating access token")) {
				handleInvalidAccessToken(message);
			} else if (message.equals("Error validating application.")) { // Access token with incorrect app ID
				throw new InvalidAuthorizationException(message);
			} else if (message.equals("Invalid access token signature.")) { // Access token that fails signature validation
				throw new InvalidAuthorizationException(message);				
			} else if (message.contains("Application does not have the capability to make this API call.") || message.contains("App must be on whitelist")) {
				throw new OperationNotPermittedException(message);
			} else if (message.contains("Invalid id") || message.contains("The parameter url is required")) { 
				throw new OperationNotPermittedException("Invalid object for this operation");
			} else if (message.contains("Duplicate status message") ) {
				throw new DuplicateStatusException(message);
			} else if (message.contains("Feed action request limit reached")) {
				throw new RateLimitExceededException();
			} else if (message.contains("The status you are trying to publish is a duplicate of, or too similar to, one that we recently posted")) {
				throw new DuplicateStatusException(message);
			}
		} else if (statusCode == HttpStatus.UNAUTHORIZED) {
			if (message.startsWith("Invalid access token")) {
				handleInvalidAccessToken(message);
			}
			throw new NotAuthorizedException(message);
		} else if (statusCode == HttpStatus.FORBIDDEN) {
			if (message.contains("Requires extended permission")) {
				String requiredPermission = message.split(": ")[1];
				throw new InsufficientPermissionException(requiredPermission);
			} else if (message.contains("Permissions error")) {
				throw new InsufficientPermissionException();
			} else if (message.contains("The user hasn't authorized the application to perform this action")) {
				throw new InsufficientPermissionException();
			} else {
				throw new OperationNotPermittedException(message);
			}
		} else if (statusCode == HttpStatus.NOT_FOUND) {
			throw new ResourceNotFoundException(message);
		} else if (statusCode == HttpStatus.INTERNAL_SERVER_ERROR) {
			throw new InternalServerErrorException(message);
		}
	}

	private void handleInvalidAccessToken(String message) {
		if (message.contains("Session has expired at unix time")) {
			throw new ExpiredAuthorizationException();
		} else if (message.contains("The session has been invalidated because the user has changed the password.")) {
			throw new RevokedAuthorizationException(message);
		} else if (message.contains("The session is invalid because the user logged out.")) {
			throw new RevokedAuthorizationException(message);
		} else if (message.contains("has not authorized application")) {
			throw new RevokedAuthorizationException(message);
		} else {
			throw new InvalidAuthorizationException(message);				
		}
	}

	private void handleUncategorizedError(ClientHttpResponse response, Map<String, Object> errorDetails) {
		try {
			super.handleError(response);
		} catch (Exception e) {
			if (errorDetails != null) {
				throw new UncategorizedApiException((String) errorDetails.get("message"), e);
			} else {
				throw new UncategorizedApiException("No error details from Uppidy", e);
			}
		}
	}

	/*
	 * Attempts to extract Uppidy error details from the response.
	 * Returns null if the response doesn't match the expected JSON error response.
	 */
	@SuppressWarnings("unchecked")
	private Map<String, Object> extractErrorDetailsFromResponse(ClientHttpResponse response) throws IOException {
		ObjectMapper mapper = new ObjectMapper(new JsonFactory());
		String json = readFully(response.getBody());
		try {
		    Map<String, Object> responseMap = mapper.<Map<String, Object>>readValue(json, new TypeReference<Map<String, Object>>() {});
		    Object error = responseMap.get("error");
		    if (error instanceof String) {
		    	Map<String, Object> result = new HashMap<String, Object>();
		    	result.put("error", (String) error);		    	
			    if (responseMap.containsKey("error_description")) {
			    	result.put("message", (String) responseMap.get("error_description"));		    	
			    }
			    return result;
		    } else {
		    	try {
		    		return (Map<String, Object>) error;
			    } catch (ClassCastException ex) {
			    	return null;
			    }
		    }
		} catch (JsonParseException e) {
			return null;
		}
	}
	
	private String readFully(InputStream in) throws IOException {
		BufferedReader reader = new BufferedReader(new InputStreamReader(in));
		StringBuilder sb = new StringBuilder();
		while (reader.ready()) {
			sb.append(reader.readLine());
		}
		return sb.toString();
	}
}
