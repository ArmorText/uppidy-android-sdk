package com.uppidy.android.sdk.api;

import java.util.List;

import org.springframework.social.ApiException;
import org.springframework.social.MissingAuthorizationException;

/**
 * Interface defining operations that can be performed on a Uppidy user
 * profiles.
 * 
 * @author arudnev@uppidy.com
 */
public interface UserOperations {

	/**
	 * Retrieves the profile for the authenticated user.
	 * 
	 * @return the user's profile information.
	 * @throws ApiException
	 *             if there is an error while communicating with Uppidy.
	 * @throws MissingAuthorizationException
	 *             if UppidyTemplate was not created with an access token.
	 */
	ApiProfile getUserProfile();

	/**
	 * Retrieves the profile for the specified user.
	 * 
	 * @param userId
	 *            the Uppidy user ID to retrieve profile data for.
	 * @return the user's profile information.
	 * @throws ApiException
	 *             if there is an error while communicating with Uppidy.
	 */
	ApiProfile getUserProfile(String userId);

	/**
	 * Searches for users.
	 * 
	 * @param query
	 *            the search query (e.g., "John Smith")
	 * @return a list of {@link ApiProfile}s, each representing a user who
	 *         matched the given query.
	 * @throws ApiException
	 *             if there is an error while communicating with Uppidy.
	 * @throws MissingAuthorizationException
	 *             if UppidyTemplate was not created with an access token.
	 */
	List<ApiProfile> search(String query);
}
