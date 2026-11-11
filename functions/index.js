const { onValueCreated } = require("firebase-functions/v2/database");
const admin = require("firebase-admin");

admin.initializeApp();

function textoSeguro(value, fallback = "") {
  return typeof value === "string" ? value.trim() : fallback;
}

function tokensUnicos(snapshot) {
  const porToken = new Map();

  snapshot.child("fcmTokens").forEach((tokenSnapshot) => {
    const token = textoSeguro(tokenSnapshot.child("token").val());
    if (!token) return false;

    const path = `fcmTokens/${tokenSnapshot.key}`;
    const current = porToken.get(token) || { token, paths: [] };
    current.paths.push(path);
    porToken.set(token, current);
    return false;
  });

  const legacy = textoSeguro(snapshot.child("fcmToken").val());
  if (legacy) {
    const current = porToken.get(legacy) || { token: legacy, paths: [] };
    current.paths.push("fcmToken");
    porToken.set(legacy, current);
  }

  return [...porToken.values()];
}

function crearChatId(uidA, uidB) {
  return uidA <= uidB ? `${uidA}_${uidB}` : `${uidB}_${uidA}`;
}

exports.onChatMessageCreated = onValueCreated(
  {
    ref: "/Chats/{chatId}/mensajes/{messageId}",
    region: "us-central1",
    serviceAccount: "mercadeo-comitan-1819c@appspot.gserviceaccount.com",
  },
  async (event) => {
    const snapshot = event.data;
    const message = snapshot.val() || {};
    const chatId = textoSeguro(event.params.chatId);
    const messageId = textoSeguro(event.params.messageId);
    const senderUid = textoSeguro(message.emisorUid);
    const receiverUid = textoSeguro(message.receptorUid);

    if (!chatId || !messageId || !senderUid || !receiverUid || senderUid === receiverUid ||
        chatId !== crearChatId(senderUid, receiverUid) || message.idMensaje !== messageId ||
        typeof message.mensaje !== "string" || message.mensaje.length > 4096) {
      console.warn("Mensaje ignorado: ruta o contenido no válido", { chatId, messageId });
      return null;
    }

    const db = admin.database();
    const [receiverSnapshot, senderSnapshot] = await Promise.all([
      db.ref(`Usuarios/${receiverUid}`).once("value"),
      db.ref(`Usuarios/${senderUid}`).once("value"),
    ]);

    if (!receiverSnapshot.exists() || !senderSnapshot.exists() ||
        receiverSnapshot.child("bloqueado").val() === true ||
        senderSnapshot.child("bloqueado").val() === true) {
      return null;
    }

    const targets = tokensUnicos(receiverSnapshot);
    if (targets.length === 0) {
      console.log("No hay tokens FCM activos para el receptor", { receiverUid });
      return null;
    }

    const senderName = textoSeguro(senderSnapshot.child("nombres").val(), "Nuevo mensaje");
    const senderAvatar = textoSeguro(senderSnapshot.child("urlImagenPerfil").val(), "");
    const isImage = textoSeguro(message.tipoMensaje) === "imagen";
    const preview = isImage
      ? "📷 Imagen"
      : textoSeguro(message.mensaje, "Tienes un nuevo mensaje")
        .replace(/\s+/g, " ")
        .slice(0, 180);

    const data = {
      notificationType: "nuevo_mensaje",
      senderUid,
      receiverUid,
      senderName: senderName.slice(0, 120),
      senderAvatar: senderAvatar.slice(0, 500),
      preview: preview || "Tienes un nuevo mensaje",
      chatId,
      messageId,
    };

    const invalidPaths = [];
    const results = await Promise.allSettled(targets.map(async (target) => {
      try {
        await admin.messaging().send({
          token: target.token,
          data,
          android: {
            priority: "high",
            ttl: 24 * 60 * 60 * 1000,
          },
        });
      } catch (error) {
        const code = error && error.code;
        if (code === "messaging/registration-token-not-registered" ||
            code === "messaging/invalid-registration-token") {
          invalidPaths.push(...target.paths.map((path) => `Usuarios/${receiverUid}/${path}`));
        }
        throw error;
      }
    }));

    const failed = results.filter((result) => result.status === "rejected");
    if (failed.length > 0) {
      console.warn("Algunos dispositivos no aceptaron la notificación", {
        receiverUid,
        failed: failed.length,
      });
    }

    if (invalidPaths.length > 0) {
      const updates = Object.fromEntries(invalidPaths.map((path) => [path, null]));
      await db.ref().update(updates);
    }

    return null;
  }
);
