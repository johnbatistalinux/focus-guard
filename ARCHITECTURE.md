# Architectural Contract — FocusGuard

## 1. Stack & Toolchain

| Camada | Escolha Tecnológica | Justificativa de Engenharia (Foco em 4 GB RAM) |
| :--- | :--- | :--- |
| **Linguagem** | Kotlin `2.0+` (JVM Target 17) | Tipagem estrita, null-safety e zero overhead em tempo de execução quando evitadas bibliotecas pesadas de reflexão. |
| **Build System** | Gradle (Kotlin DSL) + Android Gradle Plugin (AGP) | Habilitado com R8/ProGuard (`isMinifyEnabled = true`, `isShrinkResources = true`) para gerar APK `< 1.5 MB`. |
| **UI Framework** | **100% Android Native Views (`android.view.*` / `android.widget.*`)** | Zero Jetpack Compose, zero `appcompat` ou `material` pesados. As classes de View nativas já estão carregadas no processo `Zygote` do Android, resultando em ~0 MB de overhead de biblioteca. |
| **Overlay Engine** | `WindowManager` + `TYPE_ACCESSIBILITY_OVERLAY` | Dispensa a permissão separada `SYSTEM_ALERT_WINDOW`, tem latência zero (`< 50 ms`) e roda direto no contexto do `AccessibilityService`. |
| **Concorrência / Timers** | `android.os.Handler(Looper.getMainLooper())` + `SystemClock.elapsedRealtime()` | Elimina a dependência de `kotlinx-coroutines-android` (~1.5 MB + alocação de dispatchers). `Handler.postDelayed` na Main Thread para um tick de 1 Hz (1 vez por segundo) tem custo de CPU e memória desprezível. |
| **Persistência** | `android.content.SharedPreferences` (Modo Privado) | Leitura em memória instantânea suportada nativamente pelo Android sem precisar de SQLite/Room ou DataStore. |
| **Testes** | JUnit 5 (Unitários na JVM para a Máquina de Estados e Regras de Cota) | Toda a lógica de decisão (`SessionEngine` e `QuotaRepository`) é desacoplada do SDK do Android para rodar testes em milissegundos na JVM local. |

---

## 2. Diagrama de Arquitetura e Fluxo de Eventos

```mermaid
flowchart TD
    classDef os fill:#0f172a,stroke:#38bdf8,stroke-width:2px,color:#f8fafc;
    classDef service fill:#1e1b4b,stroke:#818cf8,stroke-width:2px,color:#f8fafc;
    classDef domain fill:#064e3b,stroke:#34d399,stroke-width:2px,color:#f8fafc;
    classDef ui fill:#4c1d95,stroke:#c084fc,stroke-width:2px,color:#f8fafc;
    classDef storage fill:#701a75,stroke:#f472b6,stroke-width:2px,color:#f8fafc;

    subgraph ANDROID_OS ["Camada do Sistema Operacional (Android Framework)"]
        A11Y_MGR["⚙️ AccessibilityManager<br/>• Filtro estrito por packageNames<br/>• Dispara apenas TYPE_WINDOW_STATE_CHANGED"]:::os
        WIN_MGR["🪟 WindowManager<br/>• Camada TYPE_ACCESSIBILITY_OVERLAY"]:::os
        SYS_CLOCK["⏱️ SystemClock.elapsedRealtime()<br/>• Relógio monotônico imune a fraude de hora"]:::os
    end

    subgraph APP_SERVICE ["Camada de Serviço (Processo Único Ultraleve)"]
        SVC["🛡️ FocusAccessibilityService<br/>• Recebe eventos de janela<br/>• Atualiza serviceInfo.packageNames<br/>• Executa GLOBAL_ACTION_HOME"]:::service
        TICKER["⏲️ MainThreadSessionTicker<br/>• Handler(Looper.getMainLooper())<br/>• Tick 1 Hz apenas durante pausa/sessão ativa"]:::service
    end

    subgraph CORE_DOMAIN ["Domínio Puro (100% Kotlin — Sem Dependência Android)"]
        ENGINE["🧠 FocusPolicyEngine<br/>• Máquina de estados determinística<br/>• Avalia cota diária, cooldown e sessão"]:::domain
        CATALOG["📋 SocialAppCatalog<br/>• Lista fixa curada de pacotes"]:::domain
    end

    subgraph NATIVE_UI ["Camada de Interface (Pure Android Views)"]
        OVERLAY["🛑 OverlayController<br/>• View de Pausa Consciente (10s)<br/>• View de Bloqueio (Cooldown / Cota)<br/>• Pílula Flutuante (Mini-Timer)"]:::ui
        CONFIG_ACT["📱 MainActivity (Configurações)<br/>• Switches dos apps monitorados<br/>• Ajuste de tempos e status de trava"]:::ui
    end

    subgraph PERSISTENCE ["Camada de Persistência"]
        PREFS["💾 FocusPreferencesStore<br/>• SharedPreferences wrapper<br/>• Lazy daily reset por EpochDay"]:::storage
    end

    A11Y_MGR -->|"1. notifica abertura de pacote alvo"| SVC
    SVC -->|"2. consulta decisão"| ENGINE
    ENGINE -->|"3. lê/grava estado e cotas"| PREFS
    ENGINE -->|"4. lê tempo monotônico"| SYS_CLOCK
    SVC -->|"5. comanda exibição"| OVERLAY
    OVERLAY -->|"6. anexa/remove Views nativas"| WIN_MGR
    SVC -->|"7. inicia/para contagem"| TICKER
    TICKER -->|"8. atualiza segundos"| OVERLAY
    TICKER -->|"9. notifica fim da sessão"| SVC
    CONFIG_ACT -->|"lê catálogo fixo"| CATALOG
    CONFIG_ACT -->|"salva preferências (se destravado)"| PREFS
    PREFS -.->|"notifica mudança de pacotes"| SVC
```

---

## 3. Máquina de Estados de Intervenção (`FocusPolicyEngine`)

```mermaid
stateDiagram-v2
    [*] --> IDLE : Nenhum app monitorado em 1º plano

    IDLE --> HARD_BLOCKED : App alvo abriu E (Cota Diária <= 0 OU Em Cooldown)
    IDLE --> MINDFUL_PAUSE : App alvo abriu E (Cota Diária > 0 E Sem Cooldown)

    HARD_BLOCKED --> IDLE : Usuário toca "Voltar à Home" (GLOBAL_ACTION_HOME)

    MINDFUL_PAUSE --> IDLE : Usuário desiste ("Fechar app" -> GLOBAL_ACTION_HOME)
    MINDFUL_PAUSE --> MINDFUL_PAUSE : Tick 1s (restam N segundos de pausa)
    MINDFUL_PAUSE --> ACTIVE_SESSION : Pausa concluída (0s) E usuário confirma "Iniciar Sessão"

    ACTIVE_SESSION --> SESSION_PAUSED : Usuário saiu para Home/outro app antes do fim
    SESSION_PAUSED --> ACTIVE_SESSION : Usuário retornou ao mesmo app dentro da janela
    SESSION_PAUSED --> IDLE : Janela expirou em segundo plano

    ACTIVE_SESSION --> COOLDOWN_TRIGGERED : Cronômetro da sessão zerou (00:00) OU Cota Diária zerou
    COOLDOWN_TRIGGERED --> IDLE : Executa GLOBAL_ACTION_HOME + Grava timestamp de Cooldown (10 min)
```

---

## 4. Estrutura de Diretórios e Fronteiras Modulares

O projeto segue separação estrita em pacote único com 4 subpacotes coesos:

- `com.focusguard.app/`
  - `domain/` *(100% Kotlin puro, testável na JVM sem emulador)*
    - `SocialAppCatalog.kt` — Definição imutável das redes sociais suportadas.
    - `FocusConfig.kt` — Modelo de configuração (tempos de pausa, sessão, cooldown, cota diária e pacotes ativos).
    - `AppUsageState.kt` — Estado diário e de cooldown de cada pacote.
    - `FocusPolicyEngine.kt` — Máquina de estados pura que recebe `(packageName, nowEpochMs, todayEpochDay)` e retorna a `InterventionAction`.
    - `TimeProvider.kt` — Interface para injeção de tempo monotônico e calendário local nos testes.
  - `data/`
    - `FocusPreferencesStore.kt` — Implementação enxuta sobre `SharedPreferences` com *lazy daily reset*.
  - `service/`
    - `FocusAccessibilityService.kt` — Serviço de acessibilidade do Android; gerencia o ciclo de vida, filtro `packageNames` e `GLOBAL_ACTION_HOME`.
    - `OverlayController.kt` — Constrói e gerencia as Views nativas no `WindowManager` (`TYPE_ACCESSIBILITY_OVERLAY`) reciclando instâncias quando necessário e desanexando imediatamente ao sair.
    - `MainThreadTicker.kt` — Wrapper sobre `Handler(Looper.getMainLooper())` para contagem regressiva sem alocação de coroutines.
  - `ui/`
    - `MainActivity.kt` — Tela de configuração construída com Views nativas (`ScrollView`, `LinearLayout`, `Switch`, `TextView`, `Button`), exibindo consumo do dia e aplicando a trava anti-sabotagem.

---

## 5. Contratos e Interfaces Principais (`domain`)

```kotlin
package com.focusguard.app.domain

data class TargetApp(
    val packageName: String,
    val displayName: String
)

data class FocusConfig(
    val enabledPackages: Set<String>,
    val mindfulPauseSeconds: Int = 10,
    val sessionDurationMinutes: Int = 5,
    val cooldownMinutes: Int = 10,
    val dailyQuotaMinutes: Int = 30
)

sealed interface InterventionAction {
    /** Pacote não é monitorado ou é o próprio FocusGuard/Launcher */
    data object Ignore : InterventionAction

    /** Bloqueio imediato: cota diária esgotada ou em período de cooldown */
    data class ShowHardBlock(
        val packageName: String,
        val displayName: String,
        val reason: BlockReason,
        val remainingBlockSeconds: Long
    ) : InterventionAction

    /** Barreira de respiração/pausa consciente antes de liberar a sessão */
    data class ShowMindfulPause(
        val packageName: String,
        val displayName: String,
        val pauseSeconds: Int,
        val remainingDailyQuotaSeconds: Long
    ) : InterventionAction

    /** Sessão liberada em andamento com pílula flutuante */
    data class ContinueSession(
        val packageName: String,
        val remainingSessionSeconds: Long,
        val remainingDailyQuotaSeconds: Long
    ) : InterventionAction
}

enum class BlockReason {
    COOLDOWN_ACTIVE,
    DAILY_QUOTA_EXCEEDED
}
```

---

## 6. Estratégias de Otimização Extrema para 4 GB de RAM

1. **Zero Dependências de UI (`androidx.appcompat` / `com.google.android.material` removidos):**
   - A `MainActivity` herda diretamente de `android.app.Activity` usando o tema nativo do sistema (`android:Theme.DeviceDefault.NoActionBar`).
   - Economiza ~8 MB de código DEX na memória RAM e reduz o tempo de abertura da tela de configuração para `< 40 ms`.
2. **Prevenção de Falsos Eventos de Janela (Anti-Spam de Eventos):**
   - Teclados virtuais (`com.google.android.inputmethod.latin`, Gboard, Samsung Keyboard) e diálogos do sistema (`com.android.systemui`) disparam `TYPE_WINDOW_STATE_CHANGED` mesmo enquanto o usuário está dentro do Instagram.
   - O `FocusAccessibilityService` ignora eventos de janelas de método de entrada (IME) e janelas do próprio overlay para não interromper nem reiniciar a sessão ativa.
3. **Filtro Dinâmico de Pacotes no Kernel/Framework:**
   - Sempre que `enabledPackages` é alterado no `SharedPreferences`, o serviço chama `setServiceInfo(info.apply { packageNames = enabledPackages.toTypedArray() + launcherPackages })`.
   - Incluímos o pacote do Launcher padrão na escuta apenas para saber o exato instante em que o usuário saiu da rede social e voltou para a tela inicial, permitindo pausar o cronômetro e remover a pílula flutuante sem fazer *polling*.
