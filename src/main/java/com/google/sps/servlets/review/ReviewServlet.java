package com.google.sps.servlets.review;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.appengine.api.users.User;
import com.google.appengine.api.users.UserService;
import com.google.appengine.api.users.UserServiceFactory;
import com.google.common.collect.Iterables;
import com.google.gson.Gson;

import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;

import com.google.sps.model.review.ReviewObject;
import com.google.sps.model.user.UserObject;
import com.google.sps.util.Utils;
import com.googlecode.objectify.cmd.QueryKeys;

import static com.google.sps.util.Utils.ContentType.isType;
import static com.google.sps.util.Utils.mediaItemExists;
import static com.googlecode.objectify.ObjectifyService.ofy;

@WebServlet("/reviews")
public class ReviewServlet extends HttpServlet {

    private final Gson gson = new Gson();
    private final ObjectMapper mapper = new ObjectMapper();
    private final int REVIEW_LIMIT = 10;

    /**
     * doGet() returns details of the reviews by a given user, on a given media item, or of a particular review
     * Expects ?contentType={book | movie}&contentId={id}  OR ?userId={id} OR all three of these parameters
     * Returns error 400 if the query parameters are not in either of these formats
     * Returns error 400 if a parameter is empty or invalid (e.g. "bok")
     * Returns error 404 if the given user is not found
     * Returns error 404 if a specific review is requested but not found
     * Simply returns an empty list if the given media ID does not exist to avoid API call
     * @param request: expects contentType&contentId OR userId
     * @param response: returns a JSON list of ReviewObject
     * @throws IOException
     */
    @Override
    public void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        response.setContentType("application/json; charset=utf-8");

        String userId = request.getParameter("userId");
        String contentType = request.getParameter("contentType");
        String contentId = request.getParameter("contentId");
        Integer pageNumber = Utils.parseInt(request.getParameter("pageNumber"));

        // to correctly translate pages, we need to subtract by 1 to get starting position
        if(pageNumber != null) {
            pageNumber--;
        }
        if (userId != null && contentType != null && contentId != null) {
            sendSpecificReview(userId, contentType, contentId, response);
        }
        else if (userId != null && pageNumber != null && contentType == null && contentId == null) {
            sendUserReviews(userId, pageNumber, response);
        }
        else if (userId == null && pageNumber != null && contentType != null && contentId != null) {
            sendContentReviews(contentType, contentId, pageNumber, response);
        }
        else {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST);
        }
    }

    /**
     * doPost() attempts to post a review for a given item from a user
     * Returns error 400 if the body in invalid
     * Returns error 401 if user is not authenticated
     * Returns error 409 if the user already has a review for the item
     * Returns error 500 if an error occurs with response writing
     * @param request: expects a POST body with a valid ReviewObject, except for timestamp
     * @param response: returns a JSON string of the review if successful
     * @throws IOException
     */
    @Override
    public void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException {
        response.setContentType("application/json; charset=utf-8");

        ReviewObject reviewObject;
        try {
            String body = Utils.collectRequestLines(request);
            reviewObject = mapper.readValue(body, ReviewObject.class);
        }
        catch (Exception e) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST);
            return;
        }

        if (reviewObject.getAuthorId() == null || reviewObject.getAuthorId().isEmpty()
            || reviewObject.getAuthorName() == null || reviewObject.getAuthorName().isEmpty()) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST);
            return;
        }

        UserObject userObject = getUserObject();
        if (userObject == null || !userObject.getId().equals(reviewObject.getAuthorId())) {
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED);
            return;
        }

        Boolean itemExists = mediaItemExists(reviewObject.getContentType(), reviewObject.getContentId());

        if (!validateParameters(reviewObject.getContentType(),
                reviewObject.getContentId(),
                reviewObject.getReviewTitle(),
                reviewObject.getReviewBody(),
                reviewObject.getRating()) || itemExists == null) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST);
            return;
        }
        else if (!itemExists) {
            response.sendError(HttpServletResponse.SC_NOT_FOUND);
            return;
        }

        if (Iterables.size(getMatchingReviews(userObject.getId(),
                reviewObject.getContentType(), reviewObject.getContentId())) != 0) {
            response.sendError(HttpServletResponse.SC_CONFLICT);
            return;
        }

        ofy().save().entity(reviewObject).now();

        try {
            response.getWriter().println(gson.toJsonTree(reviewObject));
        }
        catch (Exception e) {
            response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
        }
    }

    /**
     * doDelete() attempts to delete the logged in user's review for a given media item
     * Returns error 400 if any parameters are invalid
     * Returns error 401 if user is not authenticated
     * Returns error 404 if the media item or respective review is not found
     * Returns error 500 if an error occurs with deletion
     * @param request: expects contentType and contentId
     * @param response: returns OK code on success
     * @throws IOException
     */
    @Override
    public void doDelete(HttpServletRequest request, HttpServletResponse response) throws IOException {
        String contentType = request.getParameter("contentType");
        String contentId = request.getParameter("contentId");

        Boolean itemExists = mediaItemExists(contentType, contentId);
        if (itemExists == null) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST);
            return;
        }
        else if (!itemExists) {
            response.sendError(HttpServletResponse.SC_NOT_FOUND);
            return;
        }

        UserService userService = UserServiceFactory.getUserService();
        User user = userService.getCurrentUser();
        if(!userService.isUserLoggedIn()) {
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED);
            return;
        }

        tryDelete(user.getUserId(), contentType, contentId, response);
    }

    private void tryDelete(String userId, String contentType, String contentId, HttpServletResponse response)
            throws IOException {
        QueryKeys<ReviewObject> keys =  getMatchingReviews(userId, contentType, contentId);

        if(Iterables.size(keys) == 0) {
            response.sendError(HttpServletResponse.SC_NOT_FOUND);
        }
        else {
            ofy().delete().keys(keys).now();
            response.sendError(HttpServletResponse.SC_OK);
        }
    }

    private QueryKeys<ReviewObject> getMatchingReviews(String userId, String contentType, String contentId) {
        return ofy().load().type(ReviewObject.class)
                .filter("userId", userId)
                .filter("contentType", contentType)
                .filter("contentId", contentId).keys();
    }

    private void sendUserReviews(String userId, Integer pageNumber,HttpServletResponse response) throws IOException {
        if (userId.equals("")) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST);
        }
        else if (ofy().load().type(UserObject.class).id(userId).now() == null) {
            response.sendError(HttpServletResponse.SC_NOT_FOUND);
        }
        else {
            List<ReviewObject> reviews = ofy().load().type(ReviewObject.class)
                    .filter("userId", userId)
                    .limit(REVIEW_LIMIT)
                    .offset(pageNumber * REVIEW_LIMIT)
                    .order("-timestamp")
                    .list();
            response.getWriter().println(gson.toJson(reviews));
        }
    }

    private void sendContentReviews(String contentType, String contentId,
                                    Integer pageNumber, HttpServletResponse response) throws IOException {
        if (contentId.equals("") || !isType(contentType)) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST);
        }
        else {
            List<ReviewObject> reviews = ofy().load().type(ReviewObject.class)
                    .filter("contentType", contentType)
                    .filter("contentId", contentId)
                    .limit(REVIEW_LIMIT)
                    .offset(pageNumber * REVIEW_LIMIT)
                    .order("-timestamp")
                    .list();
            response.getWriter().println(gson.toJson(reviews));
        }
    }

    // TODO: Make this send a single item
    private void sendSpecificReview(String userId, String contentType, String contentId,
                                    HttpServletResponse response) throws IOException {
        if (userId.equals("") || contentId.equals("") || !isType(contentType)) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST);
        }
        else if (ofy().load().type(UserObject.class).id(userId).now() == null) {
            response.sendError(HttpServletResponse.SC_NOT_FOUND);
        }
        else {
            List<ReviewObject> reviews = ofy().load().type(ReviewObject.class)
                    .filter("userId", userId)
                    .filter("contentType", contentType)
                    .filter("contentId", contentId)
                    .list();
            if (reviews.size() > 0) {
                response.getWriter().println(gson.toJson(reviews.get(0)));
            }
            else {
                response.sendError(HttpServletResponse.SC_NOT_FOUND);
            }
        }

    }

    private UserObject getUserObject() {
        UserService userService = UserServiceFactory.getUserService();
        User user = userService.getCurrentUser();
        return (user == null) ? null : ofy().load().type(UserObject.class).id(user.getUserId()).now();
    }

    private boolean validateParameters(String contentType, String contentId,
                                       String reviewTitle, String reviewBody, Integer rating) {
        if (contentType == null || contentId == null) return false;
        if (contentId.isEmpty()) return false;
        if (!isType(contentType)) return false;
        if (rating == null || !(1 <= rating && rating <= 5)) return false;
        if (reviewTitle == null || reviewBody == null) return false;
        if (reviewTitle.isEmpty() || reviewBody.isEmpty()) return false;

        return true;
    }
}