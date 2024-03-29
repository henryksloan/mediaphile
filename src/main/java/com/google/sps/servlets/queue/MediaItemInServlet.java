package com.google.sps.servlets.queue;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.sps.model.queue.IsInListResponseObject;
import com.google.sps.model.queue.QueueListItemObject;
import com.google.sps.model.queue.ViewedListItemObject;

import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

import static com.google.sps.util.HttpUtils.setInvalidGetResponse;
import static com.google.sps.util.Utils.isCorrectListType;
import static com.googlecode.objectify.ObjectifyService.ofy;

@WebServlet("/list/isInList")
public class MediaItemInServlet extends HttpServlet {
    private final Gson gson = new GsonBuilder().serializeNulls().create();

    /**
     * doGet() returns a two field response that has a boolean if a media item is in a queue
     * or a previously watched list. useful for front end buttons
     * @param request: two query params: userId and mediaId. If any of these are null, if throws a 400
     * @param response: a 200 showing isInQueue and isInWatched
     * @throws IOException
     */
    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        IsInListResponseObject listResponse = new IsInListResponseObject();

        String userId = request.getParameter("userId");
        String mediaId = request.getParameter("mediaId");

        if(userId == null || userId.isEmpty() || mediaId == null) {
            setInvalidGetResponse(response);
            return;
        }
        listResponse.setInQueue(isInQueue(userId, mediaId));
        listResponse.setInViewed(isInViewed(userId, mediaId));

        response.getWriter().println(gson.toJsonTree(listResponse));
    }

    private boolean isInQueue(String userId, String mediaId) {
        return ofy().load().type(QueueListItemObject.class)
                .filter("userId", userId)
                .filter("mediaId", mediaId)
                .first().now() != null;
    }

    private boolean isInViewed(String userId, String mediaId) {
        return ofy().load().type(ViewedListItemObject.class)
                .filter("userId", userId)
                .filter("mediaId", mediaId)
                .first().now() != null;
    }
}
