# PickWords 3.0

Complemento de seleção múltipla de trechos de texto no Android, sem substituir o teclado Xiaomi/POCO.

## Mudança da versão 3.0

O gatilho não é o teclado. Quando o Android envia um evento de **seleção de texto**
(TYPE_VIEW_TEXT_SELECTION_CHANGED) que inclui índices válidos e o texto original,
o PickWords mostra a barra **+ Seleção | Copiar | Limpar**, mesmo que o teclado
esteja fechado. Não há linhas ou quadrados desenhados sobre a tela e não há OCR.

### Como usar

1. Ative o serviço em Acessibilidade.
2. Selecione uma palavra ou trecho com as alças nativas do Android.
3. Quando a barra aparecer, toque em **+ Seleção**.
4. Selecione outra palavra ou trecho separado, toque novamente em **+ Seleção**.
5. Toque em **Copiar**; os trechos serão unidos em ordem de adição.

### Limitações técnicas

- Acessibilidade não dispõe de evento específico "alças de seleção apareceram".
  O serviço depende da emissão de TYPE_VIEW_TEXT_SELECTION_CHANGED.
- A documentação Android define esse evento principalmente para EditText; nem
  todos os navegadores, webviews e apps com texto estático enviam um evento
  utilizável com índices válidos.
- Seleções nativas independentes não são destacadas simultaneamente; a lista
  interna do PickWords guarda os trechos adicionados.
- Acessibilidade pode acessar informações confidenciais; o aplicativo ignora
  nós sinalizados como campos de senha e não transmite texto pela rede.
- O comportamento deve ser testado em aparelho real. Build debug via GitHub Actions.

## Instalação

Actions > Build APK > artefato PickWords-debug-apk.
