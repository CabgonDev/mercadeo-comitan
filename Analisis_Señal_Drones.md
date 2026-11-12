# Análisis de Señal para Detección de Drones
## Relación Potencia (dB) vs Distancia por Banda de Frecuencia

### Parámetros del Sistema
- **Potencia de transmisión típica de drones**: +27 dBm (500 mW)
- **Ganancia de antena transmisora**: 2 dBi (típica)
- **Ganancia de antena receptora**: 3 dBi (sistema detector)
- **Modelo**: Free Space Path Loss (FSPL)

### Fórmula Utilizada
```
FSPL(dB) = 20 log₁₀(d_km) + 20 log₁₀(f_MHz) + 32.45
Potencia Recibida (dBm) = Pt + Gt + Gr - FSPL
```

---

## Tabla 1: Pérdida de Señal (FSPL en dB)

| Distancia | 400 MHz | 868 MHz | 915 MHz | 1200 MHz | 2400 MHz | 5200 MHz | 5800 MHz |
|-----------|---------|---------|---------|----------|----------|----------|----------|
| **5 m**   | 34.5    | 41.3    | 41.7    | 44.1     | 50.1     | 56.8     | 57.7     |
| **10 m**  | 40.5    | 47.3    | 47.7    | 50.1     | 56.1     | 62.8     | 63.7     |
| **25 m**  | 48.5    | 55.3    | 55.7    | 58.1     | 64.1     | 70.8     | 71.7     |
| **50 m**  | 54.5    | 61.3    | 61.7    | 64.1     | 70.1     | 76.8     | 77.7     |
| **100 m** | 60.5    | 67.3    | 67.7    | 70.1     | 76.1     | 82.8     | 83.7     |
| **250 m** | 68.5    | 75.3    | 75.7    | 78.1     | 84.1     | 90.8     | 91.7     |
| **500 m** | 74.5    | 81.3    | 81.7    | 84.1     | 90.1     | 96.8     | 97.7     |
| **1 km**  | 80.5    | 87.3    | 87.7    | 90.1     | 96.1     | 102.8    | 103.7    |
| **1.5 km**| 84.0    | 90.8    | 91.2    | 93.6     | 99.6     | 106.3    | 107.2    |
| **2 km**  | 86.5    | 93.3    | 93.7    | 96.1     | 102.1    | 108.8    | 109.7    |
| **2.5 km**| 88.5    | 95.3    | 95.7    | 98.1     | 104.1    | 110.8    | 111.7    |
| **3 km**  | 90.0    | 96.8    | 97.2    | 99.6     | 105.6    | 112.3    | 113.2    |

---

## Tabla 2: Potencia Recibida (dBm)
### Asumiendo Pt = +27 dBm, Gt = 2 dBi, Gr = 3 dBi
### **Potencia Total Efectiva = +32 dBm**

| Distancia | 400 MHz | 868 MHz | 915 MHz | 1200 MHz | 2400 MHz | 5200 MHz | 5800 MHz |
|-----------|---------|---------|---------|----------|----------|----------|----------|
| **5 m**   | -2.5    | -9.3    | -9.7    | -12.1    | -18.1    | -24.8    | -25.7    |
| **10 m**  | -8.5    | -15.3   | -15.7   | -18.1    | -24.1    | -30.8    | -31.7    |
| **25 m**  | -16.5   | -23.3   | -23.7   | -26.1    | -32.1    | -38.8    | -39.7    |
| **50 m**  | -22.5   | -29.3   | -29.7   | -32.1    | -38.1    | -44.8    | -45.7    |
| **100 m** | -28.5   | -35.3   | -35.7   | -38.1    | -44.1    | -50.8    | -51.7    |
| **250 m** | -36.5   | -43.3   | -43.7   | -46.1    | -52.1    | -58.8    | -59.7    |
| **500 m** | -42.5   | -49.3   | -49.7   | -52.1    | -58.1    | -64.8    | -65.7    |
| **1 km**  | -48.5   | -55.3   | -55.7   | -58.1    | -64.1    | -70.8    | -71.7    |
| **1.5 km**| -52.0   | -58.8   | -59.2   | -61.6    | -67.6    | -74.3    | -75.2    |
| **2 km**  | -54.5   | -61.3   | -61.7   | -64.1    | -70.1    | -76.8    | -77.7    |
| **2.5 km**| -56.5   | -63.3   | -63.7   | -66.1    | -72.1    | -78.8    | -79.7    |
| **3 km**  | -58.0   | -64.8   | -65.2   | -67.6    | -73.6    | -80.3    | -81.2    |

---

## Tabla 3: Interpretación de Niveles de Señal

| Potencia Recibida | Calidad de Detección | Observaciones |
|-------------------|---------------------|---------------|
| > -30 dBm         | 🟢 **EXCELENTE**    | Detección garantizada, señal muy fuerte |
| -30 a -50 dBm     | 🟢 **BUENA**        | Detección confiable |
| -50 a -70 dBm     | 🟡 **MODERADA**     | Detección posible, puede haber interferencias |
| -70 a -85 dBm     | 🟠 **DÉBIL**        | Detección difícil, requiere receptor sensible |
| < -85 dBm         | 🔴 **MUY DÉBIL**    | Detección poco confiable o imposible |

---

## Recomendaciones por Banda

### 🎯 **400 MHz** (Mejor para largo alcance)
- ✅ Mejor propagación, menor atenuación
- ✅ Alcance efectivo: **hasta 1.5 km** (-52 dBm)
- ⚠️ Menos común en drones comerciales
- 📡 Sensibilidad mínima requerida: -80 dBm

### 🎯 **868 MHz y 915 MHz** (Buena para medio alcance)
- ✅ Usado en sistemas de control FPV
- ✅ Alcance efectivo: **hasta 1 km** (-55 dBm)
- ✅ Buen balance entre alcance y tamaño de antena
- 📡 Sensibilidad mínima requerida: -85 dBm

### 🎯 **1200 MHz**
- ⚠️ Transición entre bandas UHF y microondas
- ✅ Alcance efectivo: **hasta 1 km** (-58 dBm)
- 📡 Sensibilidad mínima requerida: -85 dBm

### 🎯 **2.4 GHz** (Más común en drones)
- ✅ Banda más utilizada (Wi-Fi, Bluetooth, control remoto)
- ✅ Alcance efectivo: **hasta 500 m** (-58 dBm)
- ⚠️ Mayor atenuación y susceptible a interferencias
- 📡 Sensibilidad mínima requerida: -90 dBm
- 🎮 **BANDA PRIORITARIA PARA DETECCIÓN**

### 🎯 **5.2 GHz y 5.8 GHz** (Video FPV)
- ✅ Usado principalmente para transmisión de video en drones
- ⚠️ Alcance limitado: **hasta 250 m** efectivos (-59 dBm)
- ⚠️ Alta atenuación, sensible a obstáculos
- 📡 Sensibilidad mínima requerida: -95 dBm
- 📹 **IDEAL PARA DETECTAR TRANSMISIÓN DE VIDEO**

---

## Resumen de Alcances Efectivos (Señal > -60 dBm)

| Banda      | Alcance Máximo Efectivo |
|------------|------------------------|
| 400 MHz    | ~1500 m               |
| 868 MHz    | ~1000 m               |
| 915 MHz    | ~1000 m               |
| 1200 MHz   | ~1000 m               |
| 2400 MHz   | ~500 m                |
| 5200 MHz   | ~250 m                |
| 5800 MHz   | ~250 m                |

---

## Factores Adicionales a Considerar

### ⚠️ Atenuaciones Adicionales (no incluidas en FSPL básico)
- **Obstáculos urbanos**: +10 a +30 dB de pérdida adicional
- **Vegetación**: +5 a +15 dB
- **Condiciones meteorológicas**: +2 a +10 dB (lluvia, niebla)
- **Reflexiones y multipath**: puede mejorar o empeorar la señal

### 📊 Especificaciones del Receptor Necesarias
- **Sensibilidad mínima recomendada**: 
  - 400-915 MHz: -90 dBm
  - 1.2-2.4 GHz: -95 dBm
  - 5.2-5.8 GHz: -100 dBm
- **Figura de ruido**: < 3 dB
- **Ancho de banda**: Ajustable según protocolo detectado

---

## Estrategia de Detección Recomendada

1. **Prioridad 1**: Monitorear **2.4 GHz** (control remoto y telemetría)
2. **Prioridad 2**: Monitorear **5.8 GHz** (transmisión de video FPV)
3. **Prioridad 3**: Escanear **868/915 MHz** (sistemas de largo alcance)
4. **Prioridad 4**: Vigilar **400 MHz** (drones industriales/militares)

---

*Documento generado para sistema de detección de drones*  
*Basado en modelo Free Space Path Loss (FSPL)*  
*Fecha: 2024*
