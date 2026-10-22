package com.example.pickwords

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

class MainActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val pad = (20 * resources.displayMetrics.density).toInt()
        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad * 2, pad, pad)
        }
        panel.addView(TextView(this).apply {
            textSize = 17f
            text = "PickWords 3.0 — seleção nativa do Android\n\n" +
                "Agora NÃO é necessário abrir o teclado.\n\n" +
                "1. Em uma nota, mensagem ou página, mantenha o dedo pressionado " +
                "sobre uma palavra até aparecerem as alças de seleção.\n" +
                "2. Se esse aplicativo disponibilizar a seleção ao Android, " +
                "a barra PickWords aparece automaticamente.\n" +
                "3. Toque em '+ Seleção' para guardar esse trecho.\n" +
                "4. Selecione outra palavra distante, toque '+ Seleção' novamente.\n" +
                "5. Toque 'Copiar': somente os trechos guardados são copiados.\n\n" +
                "Nenhuma borda é desenhada e o texto não é lido de imagens.\n\n" +
                "IMPORTANTE: o Android não expõe diretamente a visibilidade " +
                "das alças; o serviço depende de eventos de seleção. " +
                "Alguns apps, sites e navegadores não fornecem esses eventos " +
                "ou o conteúdo selecionado. Nesse caso não é possível " +
                "detectar a seleção automaticamente por Acessibilidade.\n\n" +
                "O texto selecionado é processado localmente e não é enviado " +
                "a nenhum servidor. Evite selecionar informações sensíveis."
        })
        panel.addView(Button(this).apply {
            text = "Permitir configurações restritas"
            setOnClickListener {
                startActivity(Intent(
                    Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.parse("package:$packageName")
                ))
            }
        })
        panel.addView(Button(this).apply {
            text = "Abrir acessibilidade"
            setOnClickListener {
                startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            }
        })
        setContentView(ScrollView(this).apply { addView(panel) })
    }
}
