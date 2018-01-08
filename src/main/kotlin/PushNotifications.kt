package com.pusher

import com.google.gson.Gson
import org.apache.http.client.methods.CloseableHttpResponse
import java.io.IOException
import java.net.URISyntaxException
import org.apache.http.client.methods.HttpPost
import org.apache.http.entity.StringEntity
import org.apache.http.impl.client.HttpClients
import org.apache.http.impl.client.BasicResponseHandler

class PusherAuthError(val errorMessage: String): RuntimeException()
data class PublishNotificationResponse(val publishId: String)
data class PushNotificationErrorResponse(val error: String, val description: String)

class PushNotifications(private val instanceId: String, private val secretKey: String) {
    private val gson = Gson()
    private val interestsMaxLength = 164
    private val baseURL = "https://$instanceId.pushnotifications.pusher.com/publish_api/v1"

    init {
        if (instanceId.isEmpty()) {
            throw IllegalArgumentException("instanceId can't be an empty string")
        }

        if (secretKey.isEmpty()) {
            throw IllegalArgumentException("secretKey can't be an empty string")
        }
    }

    @Throws(IOException::class, InterruptedException::class, URISyntaxException::class)
    fun publish(interests: List<String>, publishRequest: Map<String, Any>): String {
        this.validateInterests(interests)

        val publishRequestWithInterests = publishRequest.toMutableMap()
        publishRequestWithInterests.put("interests", interests)

        System.out.println(gson.toJson(publishRequestWithInterests))
        val client = HttpClients.createDefault()
        val url = String.format("$baseURL/instances/%s/publishes", this.instanceId)
        val httpPost = HttpPost(url)
        httpPost.setEntity(StringEntity(gson.toJson(publishRequestWithInterests)))
        httpPost.setHeader("Accept", "application/json")
        httpPost.setHeader("Content-Type", "application/json")
        httpPost.setHeader("Authorization", String.format("Bearer %s", this.secretKey))
        val response = client.execute(httpPost)

        val statusCode = response.statusLine.statusCode

        when (statusCode) {
            401 -> pusherError(response)
            404 -> pusherError(response)
            in 400..499 -> pusherError(response)
            in 500..599 -> pusherError(response)
        }

        val responseString = BasicResponseHandler().handleResponse(response)

        return gson.fromJson(responseString, PublishNotificationResponse::class.java).publishId
    }

    private fun pusherError(response: CloseableHttpResponse): PusherAuthError {
        throw PusherAuthError(gson.fromJson(response.toString(), PushNotificationErrorResponse::class.java).description)
    }

    private fun validateInterests(interests: List<String>) {
        if (interests.isEmpty()) {
            throw IllegalArgumentException("Publish method expects at least one interest")
        }

        if (interests.count() == 1 && interests.first() == "") {
            throw IllegalArgumentException("interest should not be an empty string")
        }

        interests.find { it.length > interestsMaxLength }?.let {
            throw IllegalArgumentException(String.format("interest %s is longer than the maximum of %d characters", it, interestsMaxLength))
        }
    }
}
