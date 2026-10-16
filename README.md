# VolumeBooster 🔊

Aplicativo Android minimalista e utilitário de alta fidelidade desenvolvido em **Kotlin** e **Jetpack Compose**, focado exclusivamente em **ganho limpo de áudio (pré-amplificação)** para fones Bluetooth (A2DP) e fones cabeados com saída atenuada pelo sistema, com teto de até **+12.0 dB** e um **limitador de picos integrado** para evitar distorção harmônica e *clipping* nos graves.

---

## 🎯 Principais Características

- **Pré-amplificação Limpa (0.0 dB a +12.0 dB):** Ajuste contínuo com precisão de 0.1 dB via Slider e botões rápidos de presets (0 dB, +3 dB, +6 dB, +9 dB, +12 dB).
- **Limitador de Picos Estilo Poweramp (Peak Limiter):** Evita saturação digital (*digital clipping*) e estalos em frequências subgraves, garantindo headroom mesmo com ganho elevado.
- **Interface AMOLED Minimalista:** Desenvolvida inteiramente em Jetpack Compose com tema *Pure Dark* (fundo preto `#000000`), controles táteis e tipografia limpa.
- **Serviço em Primeiro Plano (Foreground Service):** Execução ininterrupta em segundo plano com notificação de controle silenciosa (`mediaPlayback`), imune ao encerramento por economia de bateria do Android.
- **Detecção Automática de Bluetooth (A2DP):** `BroadcastReceiver` inteligente que monitora conexões de fones de ouvido Bluetooth e atualiza o estado de roteamento de áudio dinamicamente.
- **Processamento via HAL de Áudio do Android:** Utiliza a API `DynamicsProcessing` associada à sessão de áudio global (sessão 0) com fallback automático para `LoudnessEnhancer`.

---

## 📐 Parâmetros do Limitador (Peak Limiter)

O limitador atua como um *brickwall limiter* de resposta rápida:

| Parâmetro | Valor Configurado | Objetivo Técnico |
|---|---|---|
| **Threshold (Limiar)** | `-0.5 dBFS` | Previne ultrapassagem de 0 dBFS no conversor DAC do Bluetooth |
| **Attack Time** | `1.0 ms` | Resposta ultrarrápida contra transitórios de pico |
| **Release Time** | `60.0 ms` | Retorno suave sem efeito de "bombeamento" (*pumping*) ou distorção em subgraves |
| **Ratio (Proporção)** | `20:1` | Curva acentuada para estancar picos acima do limiar |
| **Canais** | Stereo (L + R) | Processamento balanceado nos dois canais de áudio |

---

## 🏗️ Arquitetura do Projeto

```text
VolumeBooster/
├── app/
│   ├── src/main/
│   │   ├── AndroidManifest.xml          # Permissões de áudio, bluetooth e foreground service
│   │   ├── java/com/chrsk/volumebooster/
│   │   │   ├── AudioEngine.kt           # Motor de áudio (DynamicsProcessing / Limiter / Fallback)
│   │   │   ├── AudioService.kt          # Foreground Service de alta prioridade com notificação persistente
│   │   │   ├── BluetoothReceiver.kt     # Receptor de eventos Bluetooth A2DP e desconexão
│   │   │   ├── MainActivity.kt          # Ponto de entrada com verificação de permissões em tempo de execução
│   │   │   ├── data/
│   │   │   │   └── PreferencesManager.kt# Persistência local de parâmetros de áudio
│   │   │   └── ui/
│   │   │       ├── VolumeBoosterViewModel.kt  # ViewModel reativo conectado ao serviço
│   │   │       ├── VolumeBoosterScreen.kt     # Telas e componentes em Jetpack Compose
│   │   │       └── theme/
│   │   │           ├── Color.kt         # Paleta AMOLED Black, Cyan, Green e Amber
│   │   │           ├── Theme.kt         # Configuração de tema Material 3 Pure Dark
│   │   │           └── Type.kt          # Tipografia mono e sans-serif
│   │   └── res/                         # Ícones vetoriais, strings e estilos
│   ├── build.gradle.kts                 # Configuração do módulo app (MinSDK 28, TargetSDK 34)
│   └── proguard-rules.pro               # Regras de ofuscação e persistência do HAL
├── gradle/
│   ├── libs.versions.toml               # Catálogo centralizado de versões e dependências
│   └── wrapper/gradle-wrapper.properties# Gradle Wrapper 8.7
├── build.gradle.kts                     # Configuração raiz do projeto
├── settings.gradle.kts                  # Definição dos módulos
└── README.md
```

---

## 🔒 Permissões Utilizadas

- `android.permission.MODIFY_AUDIO_SETTINGS`: Permite anexar e configurar efeitos no subsistema de áudio.
- `android.permission.FOREGROUND_SERVICE`: Mantém o serviço de áudio rodando continuamente.
- `android.permission.FOREGROUND_SERVICE_MEDIA_PLAYBACK`: Permissão exigida a partir do Android 14 para serviços de mídia/áudio contínuos.
- `android.permission.POST_NOTIFICATIONS`: Permite exibir a notificação de controle e status no Android 13+.
- `android.permission.BLUETOOTH_CONNECT`: Permite consultar o nome do dispositivo Bluetooth conectado no Android 12+.

---

## 🚀 Como Compilar e Instalar

### Requisitos:
- Android Studio Hedgehog (2023.1.1) ou superior
- JDK 17 ou 21
- Dispositivo ou Emulador Android rodando Android 9.0 (API 28) ou superior

### Passos:
1. Clone o repositório:
   ```bash
   git clone https://github.com/chrsk3/VolumeBooster.git
   cd VolumeBooster
   ```
2. Abra a pasta no **Android Studio**.
3. Aguarde o sincronismo do Gradle (Sync Project with Gradle Files).
4. Conecte seu dispositivo Android com Depuração USB ativada e execute (`Shift + F10` ou botão Play).
5. Como alternativa via linha de comando:
   ```bash
   ./gradlew assembleDebug
   adb install -r app/build/outputs/apk/debug/app-debug.apk
   ```

---

## 📄 Licença

Distribuído sob a licença MIT. Sinta-se livre para usar e modificar.
