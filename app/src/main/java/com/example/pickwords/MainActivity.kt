package com.example.pickwords

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView

class MainActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val pad = (20 * resources.displayMetrics.density).toInt()
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad * 2, pad, pad)
        }
        col.addView(TextView(this).apply {
            textSize = 16f
            text = "PickWords\n\n" +
                "1) Toque em \"Permitir configurações restritas\" e, no menu ⋮, " +
                "ative \"Permitir configurações restritas\" (necessário em apps instalados fora da Play Store).\n\n" +
                "2) Toque em \"Abrir acessibilidade\", escolha PickWords e ative.\n\n" +
                "3) Um botão ✎ aparecerá na lateral. Toque nele em qualquer tela, " +
                "toque ou deslize o dedo sobre as palavras desejadas e aperte Copiar.\n\n" +
                "Dica Xiaomi/HyperOS: em Bateria do app, escolha \"Sem restrições\" e ative \"Início automático\"."
        })
        col.addView(Button(this).apply {
            text = "Permitir configurações restritas"
            setOnClickListener {
                startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.parse("package:$packageName")))
            }
        })
        col.addView(Button(this).apply {
            text = "Abrir acessibilidade"
            setOnClickListener { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
        })
        setContentView(col)
    }
}
