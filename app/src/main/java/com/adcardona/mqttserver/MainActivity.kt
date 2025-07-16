package com.adcardona.mqttserver

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.lifecycleScope
import com.adcardona.mqttserver.ui.theme.MQTTServerTheme
import com.hivemq.client.mqtt.MqttClient
import com.hivemq.client.mqtt.mqtt5.Mqtt5BlockingClient
import com.hivemq.client.mqtt.mqtt5.message.publish.Mqtt5Publish
import com.hivemq.client.mqtt.datatypes.MqttQos
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.function.Consumer
import kotlin.text.Charsets.UTF_8

class MainActivity : ComponentActivity() {

    private var mqttClient: Mqtt5BlockingClient? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MQTTServerTheme {
                MQTTApp()
            }
        }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    fun MQTTApp() {
        // Estados
        var estatusConexion by remember { mutableStateOf("Desconectado") }
        var valorSensor by remember { mutableStateOf("Sin datos") }
        var estatusLed by remember { mutableStateOf(false) }
        var conectando by remember { mutableStateOf(false) }
        var mensajes by remember { mutableStateOf(listOf<String>()) }

        // MQTT
        val host = "e9b7179ea7f54e56a657a9e7b4a416f1.s1.eu.hivemq.cloud"
        val username = "admin"
        val password = "Password5"

        fun conectarMQTT() {
            conectando = true
            lifecycleScope.launch {
                try {
                    withContext(Dispatchers.IO) {
                        mqttClient = MqttClient.builder()
                            .useMqttVersion5()
                            .serverHost(host)
                            .serverPort(8883)
                            .sslWithDefaultConfig()
                            .buildBlocking()

                        val connectionResult = mqttClient?.connectWith()
                            ?.simpleAuth()
                            ?.username(username)
                            ?.password(UTF_8.encode(password))
                            ?.applySimpleAuth()
                            ?.send()

                        if (connectionResult?.reasonCode?.isError == false) {
                            mqttClient?.subscribeWith()
                                ?.topicFilter("valor-analogico")
                                ?.qos(MqttQos.AT_LEAST_ONCE)
                                ?.send()

                            mqttClient?.toAsync()?.publishes(
                                com.hivemq.client.mqtt.MqttGlobalPublishFilter.ALL,
                                Consumer { publish: Mqtt5Publish ->
                                    val topic = publish.topic.toString()
                                    val payload = String(publish.payloadAsBytes, UTF_8)

                                    lifecycleScope.launch {
                                        if (topic == "valor-analogico") {
                                            valorSensor = payload
                                        }
                                        mensajes = mensajes + "${System.currentTimeMillis()}: $topic = $payload"
                                        if (mensajes.size > 10) {
                                            mensajes = mensajes.takeLast(10)
                                        }
                                    }
                                }
                            )
                        }
                    }

                    estatusConexion = "Conectado ✅"
                    conectando = false

                } catch (e: Exception) {
                    estatusConexion = "Error: ${e.message}"
                    conectando = false
                }
            }
        }

        fun controlarLED(state: Boolean) {
            lifecycleScope.launch {
                try {
                    withContext(Dispatchers.IO) {
                        mqttClient?.publishWith()
                            ?.topic("control-led")
                            ?.payload(UTF_8.encode(if (state) "1" else "0"))
                            ?.send()
                    }
                    estatusLed = state
                } catch (e: Exception) {
                    mensajes = mensajes + "Error enviando comando LED: ${e.message}"
                }
            }
        }

        fun desconectar() {
            lifecycleScope.launch {
                try {
                    withContext(Dispatchers.IO) {
                        mqttClient?.disconnect()
                    }
                    estatusConexion = "Desconectado"
                    valorSensor = "Sin datos"
                    mqttClient = null
                } catch (e: Exception) {
                    mensajes = mensajes + "Error desconectando: ${e.message}"
                }
            }
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(20.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {

            Text(
                text = "ESP32 MQTT Controller",
                fontSize = 24.sp,
                color = MaterialTheme.colorScheme.primary
            )

            Spacer(modifier = Modifier.height(20.dp))

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = if (estatusConexion.contains("Conectado"))
                        Color.Green.copy(alpha = 0.1f)
                    else
                        Color.Red.copy(alpha = 0.1f)
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Estado: $estatusConexion",
                        fontSize = 16.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                Button(
                    onClick = { conectarMQTT() },
                    enabled = !conectando && estatusConexion != "Conectado ✅"
                ) {
                    if (conectando) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                    } else {
                        Text("Conectar")
                    }
                }

                Button(
                    onClick = { desconectar() },
                    enabled = estatusConexion == "Conectado ✅",
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text("Desconectar")
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Sensor LDR",
                        fontSize = 18.sp,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "Valor: $valorSensor",
                        fontSize = 16.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Control LED",
                        fontSize = 18.sp,
                        color = MaterialTheme.colorScheme.primary
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        Button(
                            onClick = { controlarLED(true) },
                            enabled = estatusConexion == "Conectado ✅",
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color.Green
                            )
                        ) {
                            Text("Encender")
                        }

                        Button(
                            onClick = { controlarLED(false) },
                            enabled = estatusConexion == "Conectado ✅",
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color.Red
                            )
                        ) {
                            Text("Apagar")
                        }
                    }

                    Text(
                        text = "Estado: ${if (estatusLed) "Encendido 💡" else "Apagado"}",
                        fontSize = 14.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Mensajes Recientes",
                        fontSize = 18.sp,
                        color = MaterialTheme.colorScheme.primary
                    )

                    if (mensajes.isEmpty()) {
                        Text(
                            text = "No hay mensajes",
                            fontSize = 14.sp,
                            color = Color.Gray
                        )
                    } else {
                        mensajes.takeLast(5).forEach { message ->
                            Text(
                                text = message,
                                fontSize = 12.sp,
                                modifier = Modifier.padding(vertical = 2.dp)
                            )
                        }
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        lifecycleScope.launch {
            withContext(Dispatchers.IO) {
                mqttClient?.disconnect()
            }
        }
    }
}