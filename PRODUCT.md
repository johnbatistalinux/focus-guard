# Product Specification — FocusGuard (Ultra-Light Anti-Doomscrolling)

## 1. Problema & Proposta de Valor

### O Problema
Aplicativos comerciais de bloqueio de redes sociais e controle de tempo de tela (StayFree, AppBlock, One Sec) sofrem de dois problemas críticos para aparelhos de entrada/intermediários (como dispositivos com **4 GB de RAM**):
1. **Bloatware e Consumo Excessivo de RAM (150 MB – 300 MB):** Carregam dezenas de SDKs de anúncios (AdMob, UnityAds), rastreadores de telemetria, bancos de dados pesados e frameworks híbridos que engasgam o sistema e acionam o *Low Memory Killer* (LMK) do Android.
2. **Polling Ineficiente e Dreno de Bateria:** Serviços mal arquitetados rodam loops contínuos em segundo plano em vez de reagir a eventos do sistema operacional.

### Proposta de Valor
O **FocusGuard** é um interceptador nativo de redes sociais para Android projetado sob a filosofia **Zero-Bloat**:
- **Orçamento Estrito de Memória:** Consumo em segundo plano entre **5 MB e 12 MB de RAM**, utilizando 100% componentes nativos pré-carregados no processo `Zygote` do Android (sem Jetpack Compose, sem bibliotecas de terceiros).
- **Zero CPU Ociosa (0% Idle CPU):** Arquitetura 100% orientada a eventos do kernel/framework via `AccessibilityService` com filtro dinâmico de pacotes (`packageNames`).
- **Privacidade Total (Zero-Network):** O aplicativo não declara sequer a permissão `android.permission.INTERNET` no manifesto. Zero anúncios, zero telemetria.

### Métricas de Sucesso (SLAs Técnicos)
| Métrica | Meta Estrita |
| :--- | :--- |
| **Consumo de RAM (Serviço em 2º Plano)** | `< 15 MB` (Alvo: `5–12 MB` no `dumpsys meminfo`) |
| **Uso de CPU em Ociosidade** | `0.0%` quando nenhum app monitorado está em transição |
| **Latência de Interceptação** | `< 50 ms` (zero flash do feed antes da barreira visual) |
| **Tamanho do APK Final (Release)** | `< 1.5 MB` |
| **Permissões Exigidas** | Apenas `BIND_ACCESSIBILITY_SERVICE` (usa `TYPE_ACCESSIBILITY_OVERLAY` nativo) |

---

## 2. Persona & Jornadas do Usuário

### Persona Principal
Usuário de smartphone Android com 4 GB de RAM que deseja eliminar o hábito de *deep scrolling* (rolagem infinita automática) no Instagram e redes sociais sem sacrificar a fluidez do sistema operacional nem ser bombardeado por anúncios.

### Jornada 1: Configuração Inicial (Onboarding Enxuto)
1. Usuário abre o **FocusGuard**.
2. Vê o status do Serviço de Acessibilidade e um botão direto para ativá-lo nas Configurações do Android.
3. Na lista enxuta de redes sociais pré-mapeadas (Instagram, TikTok, YouTube, X/Twitter, Reddit, Facebook, Threads, Kwai), ativa os switches dos apps que deseja monitorar.
4. Ajusta (ou mantém os padrões recomendados) os 3 parâmetros globais:
   - **Tempo da Pausa Consciente** (Padrão: `10 segundos`)
   - **Duração da Sessão Liberada** (Padrão: `5 minutos`)
   - **Cooldown Pós-Sessão** (Padrão: `10 minutos`)
   - **Cota Máxima Diária** (Padrão: `30 minutos`)

### Jornada 2: Tentativa de Abertura por Impulso (Fricção Consciente)
1. Usuário toca no ícone do **Instagram**.
2. Instantaneamente (`< 50 ms`), antes de ver qualquer post, o `FocusAccessibilityService` injeta o overlay em tela cheia (`TYPE_ACCESSIBILITY_OVERLAY`).
3. A tela escura minimalista exibe:
   - Contador regressivo da **Pausa Consciente** (`10s... 9s...`).
   - Quanto tempo da cota diária ainda resta hoje (ex: `Restam 20 min hoje`).
   - Botão primário destacado: **"Fechar e voltar ao que importa"** (aciona `GLOBAL_ACTION_HOME` imediatamente).
   - Botão secundário (desabilitado até zerar os 10s): **"Liberar sessão de 5 min"**.

### Jornada 3: Uso Consciente com Mini-Cronômetro Flutuante
1. Caso o usuário aguarde os 10s e confirme a entrada, o overlay em tela cheia é desmontado e substituído por uma **pílula flutuante minimalista** no canto superior da tela (`04:59`).
2. Se o usuário sair do Instagram antes dos 5 minutos (for para a Home ou para o WhatsApp), o cronômetro pausa imediatamente e a pílula some da tela.
3. Quando o cronômetro da sessão chega a `00:00`:
   - O FocusGuard executa `performGlobalAction(GLOBAL_ACTION_HOME)` expulsando o usuário do feed.
   - Inicia o **Cooldown Pós-Sessão de 10 minutos** para aquele aplicativo.

### Jornada 4: Bloqueio por Cooldown ou Cota Diária Esgotada
1. Se o usuário tentar reabrir o Instagram durante os 10 minutos de Cooldown (ou após gastar os 30 minutos diários), o overlay exibe a tela de **Bloqueio Estrito**:
   - Mostra o tempo restante de Cooldown (ou informa que a cota do dia acabou até a meia-noite).
   - Único botão disponível: **"Voltar para a Tela Inicial"**.

---

## 3. Regras de Negócio & Invariantes Não-Negociáveis

1. **Filtro no Nível do Sistema Operacional:** O serviço de acessibilidade **nunca** deve escutar todos os pacotes do aparelho (`packageNames = null` é proibido). Sempre que o usuário ligar/desligar um app na lista, o `AccessibilityServiceInfo.packageNames` deve ser atualizado apenas com os pacotes ativos.
2. **Desalocação Imediata de Views:** Quando não houver barreira nem sessão ativa em tela, nenhuma `View` de overlay pode permanecer anexada ao `WindowManager`.
3. **Trava Anti-Sabotagem (Anti-Bypass):**
   - Enquanto qualquer aplicativo monitorado estiver em **Cooldown** ou com a **Cota Diária Esgotada**, a tela de configurações do FocusGuard entra em **Modo Somente Leitura (Trava Ativa)**: o usuário não pode desativar o switch do app nem aumentar o limite diário até que o cooldown termine ou o dia vire.
4. **Reset Diário Automático (Lazy Evaluation):** Não usar `AlarmManager` em segundo plano à meia-noite para zerar contadores. O reset diário deve ser calculado de forma preguiçosa (*lazy*) comparando a data atual (`LocalDate` / epoch day) no momento em que o usuário interage com o serviço ou abre o app.
5. **Catálogo Fixo Curado:** Sem uso da permissão `QUERY_ALL_PACKAGES`. O catálogo contém os pacotes oficiais:
   - Instagram (`com.instagram.android`)
   - TikTok (`com.zhiliaoapp.musically` / `com.ss.android.ugc.trill`)
   - YouTube (`com.google.android.youtube`)
   - X / Twitter (`com.twitter.android`)
   - Reddit (`com.reddit.frontpage`)
   - Threads (`com.instagram.barcelona`)
   - Facebook (`com.facebook.katana`)
   - Kwai (`com.kwai.video`)
