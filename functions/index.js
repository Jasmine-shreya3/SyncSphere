
const { onCall, HttpsError } = require("firebase-functions/v2/https");
const { defineSecret } = require("firebase-functions/params");
const { AccessToken } = require("livekit-server-sdk");

const LIVEKIT_API_KEY = defineSecret("LIVEKIT_API_KEY");
const LIVEKIT_API_SECRET = defineSecret("LIVEKIT_API_SECRET");
const LIVEKIT_URL = defineSecret("LIVEKIT_URL");

exports.createLiveKitToken = onCall(
  {
    secrets: [
      LIVEKIT_API_KEY,
      LIVEKIT_API_SECRET,
      LIVEKIT_URL,
    ],
  },
  async (request) => {
    if (!request.auth) {
      throw new HttpsError(
        "unauthenticated",
        "Sign in before starting a call."
      );
    }

    const roomName = request.data?.roomName;

    if (
      typeof roomName !== "string" ||
      !/^[a-zA-Z0-9_-]{1,100}$/.test(roomName)
    ) {
      throw new HttpsError(
        "invalid-argument",
        "Please provide a valid room name."
      );
    }

    const token = new AccessToken(
      LIVEKIT_API_KEY.value(),
      LIVEKIT_API_SECRET.value(),
      {
        identity: request.auth.uid,
        ttl: "1h",
      }
    );

    token.addGrant({
      roomJoin: true,
      room: roomName,
      canPublish: true,
      canSubscribe: true,
    });

    return {
      token: await token.toJwt(),
      url: LIVEKIT_URL.value(),
      roomName,
    };
  }
);
