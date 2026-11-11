const { onValueCreated } = require("firebase-functions/v2/database");
const { onSchedule } = require("firebase-functions/v2/scheduler");
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

async function enviarNotificacion(targets, data, db, userUid) {
  const invalidPaths = [];
  const results = await Promise.allSettled(targets.map(async (target) => {
    try {
      await admin.messaging().send({
        token: target.token,
        data,
        android: {
          priority: data.type === "chat_message" ? "high" : "normal",
          ttl: 24 * 60 * 60 * 1000,
        },
      });
    } catch (error) {
      const code = error && error.code;
      if (code === "messaging/registration-token-not-registered" ||
          code === "messaging/invalid-registration-token") {
        invalidPaths.push(...target.paths.map((path) => `Usuarios/${userUid}/${path}`));
      }
      throw error;
    }
  }));

  const failed = results.filter((result) => result.status === "rejected");
  if (failed.length > 0) {
    console.warn("Algunos dispositivos no aceptaron la notificación", {
      userUid,
      failed: failed.length,
    });
  }

  if (invalidPaths.length > 0) {
    const updates = Object.fromEntries(invalidPaths.map((path) => [path, null]));
    await db.ref().update(updates);
  }

  return results;
}

// ============================================
// 1. NOTIFICACIÓN DE MENSAJE DE CHAT
// ============================================
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
      type: "chat_message",
      notificationType: "nuevo_mensaje",
      senderUid,
      receiverUid,
      senderName: senderName.slice(0, 120),
      senderAvatar: senderAvatar.slice(0, 500),
      preview: preview || "Tienes un nuevo mensaje",
      chatId,
      messageId,
    };

    await enviarNotificacion(targets, data, db, receiverUid);
    return null;
  }
);

// ============================================
// 2. NOTIFICACIÓN DE NUEVO ANUNCIO EN CATEGORÍA FAVORITA
// ============================================
exports.onAnuncioCreated = onValueCreated(
  {
    ref: "/Anuncios/{anuncioId}",
    region: "us-central1",
    serviceAccount: "mercadeo-comitan-1819c@appspot.gserviceaccount.com",
  },
  async (event) => {
    const snapshot = event.data;
    const anuncio = snapshot.val() || {};
    const anuncioId = textoSeguro(event.params.anuncioId);
    const categoria = textoSeguro(anuncio.categoria);
    const titulo = textoSeguro(anuncio.titulo);
    const precio = textoSeguro(anuncio.precio);
    const uidVendedor = textoSeguro(anuncio.uid);

    if (!anuncioId || !categoria || !titulo || !uidVendedor) {
      console.warn("Anuncio inválido", { anuncioId });
      return null;
    }

    const db = admin.database();

    // Buscar usuarios que tienen esta categoría como favorita
    const usuariosSnapshot = await db.ref("Usuarios").once("value");
    const notificacionesEnviadas = [];

    usuariosSnapshot.forEach((userSnapshot) => {
      const userUid = userSnapshot.key;

      // No notificar al propio vendedor
      if (userUid === uidVendedor) return false;

      const categoriasFavoritas = userSnapshot.child("categoriasFavoritas").val() || {};

      // Si tiene esta categoría como favorita
      if (categoriasFavoritas[categoria] === true) {
        const targets = tokensUnicos(userSnapshot);

        if (targets.length > 0) {
          const data = {
            type: "new_anuncio_category",
            anuncioId,
            titulo: titulo.slice(0, 120),
            categoria: categoria.slice(0, 50),
            precio: precio.slice(0, 50),
          };

          notificacionesEnviadas.push(
            enviarNotificacion(targets, data, db, userUid)
          );
        }
      }

      return false;
    });

    await Promise.allSettled(notificacionesEnviadas);
    console.log(`Notificaciones enviadas para nuevo anuncio: ${notificacionesEnviadas.length}`);
    return null;
  }
);

// ============================================
// 3. RECORDATORIOS DE ANUNCIOS SIN RESPUESTA (Cron diario)
// ============================================
exports.checkAnunciosSinRespuesta = onSchedule(
  {
    schedule: "0 9 * * *", // Todos los días a las 9 AM
    timeZone: "America/Mexico_City",
    region: "us-central1",
  },
  async () => {
    const db = admin.database();
    const ahora = Date.now();
    const tresDiasEnMs = 3 * 24 * 60 * 60 * 1000;

    const anunciosSnapshot = await db.ref("Anuncios").once("value");
    const recordatoriosEnviados = [];

    anunciosSnapshot.forEach((anuncioSnapshot) => {
      const anuncio = anuncioSnapshot.val();
      const anuncioId = anuncioSnapshot.key;
      const fechaPublicacion = anuncio.fecha || 0;
      const uidVendedor = textoSeguro(anuncio.uid);
      const titulo = textoSeguro(anuncio.titulo);

      // Si el anuncio tiene más de 3 días
      if (ahora - fechaPublicacion > tresDiasEnMs) {
        // Verificar si tiene mensajes
        const chatId = `*_${uidVendedor}`; // Buscar chats donde es vendedor

        db.ref("Chats")
          .orderByKey()
          .startAt(uidVendedor)
          .endAt(uidVendedor + "")
          .once("value")
          .then((chatsSnapshot) => {
            let tieneMensajes = false;

            chatsSnapshot.forEach((chatSnapshot) => {
              const mensajes = chatSnapshot.child("mensajes").val();
              if (mensajes && Object.keys(mensajes).length > 0) {
                tieneMensajes = true;
                return true; // break
              }
              return false;
            });

            // Si NO tiene mensajes, enviar recordatorio
            if (!tieneMensajes) {
              db.ref(`Usuarios/${uidVendedor}`).once("value").then((userSnapshot) => {
                if (userSnapshot.exists()) {
                  const targets = tokensUnicos(userSnapshot);

                  if (targets.length > 0) {
                    const diasSinRespuesta = Math.floor((ahora - fechaPublicacion) / (24 * 60 * 60 * 1000));

                    const data = {
                      type: "anuncio_no_response",
                      anuncioId,
                      titulo: titulo.slice(0, 120),
                      dias: diasSinRespuesta.toString(),
                    };

                    recordatoriosEnviados.push(
                      enviarNotificacion(targets, data, db, uidVendedor)
                    );
                  }
                }
              });
            }
          });
      }

      return false;
    });

    await Promise.allSettled(recordatoriosEnviados);
    console.log(`Recordatorios enviados: ${recordatoriosEnviados.length}`);
    return null;
  }
);

// ============================================
// 4. NOTIFICACIÓN DE CAMBIOS EN FAVORITOS
// ============================================
exports.onAnuncioUpdated = onValueCreated(
  {
    ref: "/Anuncios/{anuncioId}/precio",
    region: "us-central1",
    serviceAccount: "mercadeo-comitan-1819c@appspot.gserviceaccount.com",
  },
  async (event) => {
    const anuncioId = textoSeguro(event.params.anuncioId);
    const nuevoPrecio = textoSeguro(event.data.val());

    if (!anuncioId || !nuevoPrecio) return null;

    const db = admin.database();
    const anuncioSnapshot = await db.ref(`Anuncios/${anuncioId}`).once("value");

    if (!anuncioSnapshot.exists()) return null;

    const anuncio = anuncioSnapshot.val();
    const titulo = textoSeguro(anuncio.titulo);
    const precioAnterior = textoSeguro(anuncio.precioAnterior || anuncio.precio);

    // Buscar usuarios que tienen este anuncio en favoritos
    const usuariosSnapshot = await db.ref("Usuarios").once("value");
    const notificacionesEnviadas = [];

    usuariosSnapshot.forEach((userSnapshot) => {
      const userUid = userSnapshot.key;
      const favoritos = userSnapshot.child("favoritos").val() || {};

      if (favoritos[anuncioId] === true) {
        const targets = tokensUnicos(userSnapshot);

        if (targets.length > 0) {
          const data = {
            type: "anuncio_favorite",
            anuncioId,
            titulo: titulo.slice(0, 120),
            cambio: `Bajó de precio: ${precioAnterior} → ${nuevoPrecio}`,
          };

          notificacionesEnviadas.push(
            enviarNotificacion(targets, data, db, userUid)
          );
        }
      }

      return false;
    });

    await Promise.allSettled(notificacionesEnviadas);
    console.log(`Notificaciones de favorito enviadas: ${notificacionesEnviadas.length}`);
    return null;
  }
);
