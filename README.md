# PickWords 2.0

Complemento experimental para selecionar palavras não consecutivas em campos de texto do Android/HyperOS sem substituir o teclado.

## Como funciona

O serviço de Acessibilidade detecta uma janela do teclado (IME) e, se houver um campo editável focado, mostra uma pequena barra acima do teclado. Nada de botão flutuante, OCR, simulação de fotografia ou caixas sobre o texto.

1. Toque no campo de texto e abra o teclado normal.
2. Toque numa palavra para posicionar o cursor.
3. Toque em **+ Palavra**, repita com palavras separadas.
4. Toque em **Copiar**. Só as palavras escolhidas são copiadas, na ordem do texto.

Uma seleção nativa de várias palavras também pode ser adicionada pressionando **+ Palavra**.

## Limitações

- O Android não expõe múltiplos destaques nativos simultâneos em outro editor. As faixas ficam na lista interna do PickWords.
- Depende do acesso à janela do teclado e à posição do cursor pelo serviço de Acessibilidade, que pode não funcionar em todos os apps/versões do HyperOS.
- Campos de senha são ignorados.
- O texto só é lido do campo editável em foco ao acionar + Palavra; não é enviado a servidores.
- É uma build debug; a assinatura de versões diferentes pode exigir a desinstalação do APK anterior.

O workflow Build APK do GitHub Actions compila e disponibiliza o artefato PickWords-debug-apk.
