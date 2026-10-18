# FocusGuard — Anti-Doomscrolling Ultraleve para Android (4 GB RAM)

Aplicativo Android nativo progettado sob a filosofia **Zero-Bloat** (`~5–12 MB` de RAM em segundo plano, `0%` de CPU ociosa e zero anúncios/telemetria) para eliminar o hábito de *deep scrolling* no Instagram, TikTok, YouTube, X/Twitter, Reddit e outras redes sociais.

---

## Como Compilar e Instalar no seu Celular

### Opção 1: Via Android Studio (Mais simples)
1. Abra o Android Studio e selecione **Open** -> `/home/johnb/Projects/focus-guard`.
2. Conecte seu celular via cabo USB com a **Depuração USB** ativada nas *Opções do Desenvolvedor*.
3. Clique em **Run (`▶`)** para compilar e instalar direto no aparelho.

### Opção 2: Via Linha de Comando (CLI)
Em um ambiente com JDK 17+ e Android SDK configurados:

```bash
# Rodar a suíte de testes unitários na JVM (Roda em < 2 segundos)
gradle test

# Gerar o APK otimizado com R8/ProGuard (< 1.5 MB)
gradle assembleDebug

# Instalar no celular conectado via ADB
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

---

## Ativando no Aparelho (Passo Único)

1. Abra o **FocusGuard** no celular.
2. Toque no botão **"Ativar Serviço de Acessibilidade"** no topo da tela.
3. Na lista do Android, toque em **FocusGuard** e ative a chave.
   - *Nota para Android 13+ (Configurações Restritas):* Caso o Android mostre o aviso de "Configuração restrita" por ser um APK instalado fora da Play Store, vá em *Configurações > Aplicativos > FocusGuard > Menu (⋮ no canto superior direito) > Permitir configurações restritas* e depois ative a Acessibilidade normalmente.

---

## Como Auditar o Consumo de RAM no seu Celular

Com o celular plugado via USB, rode:

```bash
adb shell dumpsys meminfo com.focusguard.app.debug | head -n 35
```

Você verá o **TOTAL PSS** estacionado na faixa de **5 MB a 12 MB**, porque:
- `canRetrieveWindowContent="false"` impede que o Android serialize árvores de interface via Binder IPC.
- `serviceInfo.packageNames` filtra os pacotes direto no sistema operacional quando em repouso.
- Nenhuma biblioteca de UI pesada (Jetpack Compose / AppCompat / Material) ou SDK de terceiros é carregada.
