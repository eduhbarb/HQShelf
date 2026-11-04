# 📚 HQShelf

[![Baixar APK mais recente](https://img.shields.io/github/v/release/SEU_USUARIO/HQShelf?label=vers%C3%A3o&color=blue)](https://github.com/SEU_USUARIO/HQShelf/releases/latest)

App Android para organizar sua coleção de quadrinhos (HQs, mangás, graphic novels) em prateleiras digitais.

Adicione arquivos CBR, CBZ, EPUB, PDF e MOBI, navegue pela estante, abra o leitor de sua preferência e acompanhe tudo com um widget na tela inicial.

---

## Objetivo

Centralizar a coleção de quadrinhos em um só lugar, sem depender de nuvem ou cadastro. Tudo fica armazenado localmente no aparelho, usando o sistema de arquivos do Android (SAF) e um banco de dados Room.

---

## Funcionalidades

| Recurso | Descrição |
|---|---|
| 📦 Adicionar HQs | Importa CBR/CBZ/EPUB/PDF/MOBI via seletor de arquivos (SAF). |
| 🏠 Prateleira | Lista todos os quadrinhos cadastrados com capa e título. |
| 🔁 Reordenar | Arrasta itens para mudar a ordem na prateleira. |
| 🗑️ Remover | Exclui um item da estante (opcionalmente do dispositivo). |
| 📖 Abrir leitor | Lança um proxy que encaminha o arquivo para o leitor instalado. |
| 🧩 Widget | Widget de tela inicial com acesso rápido à prateleira. |
| 💾 Local-only | Banco Room no aparelho; nenhuma conta, nenhum servidor. |

---

## Como funciona

O app segue uma arquitetura simples em camadas:

```
UI (Jetpack Compose)
    ↓
ViewModel + UseCases
    ↓
Repository (implementação local)
    ↓
Banco de dados Room (Comic / ShelfItem)
```

- **UI:** `MainActivity` + `ShelfScreen`, construído 100% em Jetpack Compose.
- **Domínio:** Use cases isolados (`AddComicUseCase`, `RemoveComicUseCase`, `GetShelfUseCase`, `ReorderComicUseCase`).
- **Dados:** `ShelfRepositoryImpl` + `ShelfLocalDataSource` + DAO Room.
- **Utilitários:** `ComicExtractor` (lê metadados do arquivo, incluindo CBR via **junrar**), `FileProviderUtil` (exposição segura de arquivos via `FileProvider`), `ReaderProxyActivity` (abre no leitor externo).
- **Widget:** `ShelfGlanceWidget` (Glance) + `ShelfWidgetReceiver` + `ShelfWidgetUpdater`.

---

## Tecnologias

- **Linguagem:** Kotlin
- **UI:** Jetpack Compose (Material 3)
- **Banco:** Room (SQLite local)
- **Widget:** Glance (AppWidget moderno)
- **Arquivos:** Storage Access Framework + FileProvider
- **Extração CBR:** [junrar](https://github.com/junrar/junrar) (leitura de arquivos RAR/CBR)
- **Build:** Gradle + Android Gradle Plugin 8.1.0
- **compileSdk / targetSdk:** 34
- **minSdk:** 34

---

## Requisitos para buildar no seu PC

| Item | Versão mínima |
|---|---|
| Android Studio | Giraffe (2024.2.1) ou mais recente |
| JDK | 17 |
| Android SDK | compileSdk 34 |
| build-tools | 34.0.0 |
| Gradle | via wrapper (`gradlew`) |

> **Nota:** o `minSdk` está definido como **34** (Android 14). Se quiser rodar em aparelhos mais antigos, altere `minSdk` no `app/build.gradle` e teste a compatibilidade.

### Dependência externa: junrar

O suporte a arquivos **CBR** (RAR) usa a biblioteca **junrar**. Adicione ao `app/build.gradle`, dentro de `dependencies {}`:

```gradle
implementation 'com.github.junrar:junrar:...'
```

Verifique a versão mais recente no repositório oficial do junrar e sincronize o Gradle depois de adicionar.

### 1. Baixe o repositório

```bash
git clone https://github.com/SEU_USUARIO/HQShelf.git
cd HQShelf
```

### 2. Configure o SDK local

Crie o arquivo `local.properties` na raiz do projeto (se ainda não existir):

```properties
sdk.dir=C:\\Users\\SEU_USUARIO\\AppData\\Local\\Android\\Sdk
```

Ajuste o caminho conforme a instalação do Android SDK no seu computador.

### 3. Sincronize o Gradle

Abra o projeto no Android Studio e clique em **"Sync Now"** quando solicitado, ou rode no terminal:

```powershell
.\gradlew --refresh-dependencies
```

### 4. Gere o APK de depuração

```powershell
.\gradlew assembleDebug
```

O APK fica em:

```
app/build/outputs/apk/debug/app-debug.apk
```

Instale no aparelho ou emulador:

```powershell
.\gradlew installDebug
```

### 5. Versão de release (opcional)

Para gerar um APK assinado, crie um keystore e adicione ao `app/build.gradle` (ou assine manualmente via Android Studio: **Build → Generate Signed Bundle / APK**).

---

## Estrutura do projeto

```
HQShelf/
├── app/
│   ├── build.gradle
│   └── src/
│       └── main/
│           ├── AndroidManifest.xml
│           ├── java/com/hqshelf/
│           │   ├── domain/
│           │   ├── data/
│           │   ├── ui/
│           │   └── widget/
│           └── res/
│               ├── drawable/
│               ├── values/
│               └── xml/
├── gradle/
├── build.gradle
├── settings.gradle
├── gradle.properties
├── gradlew / gradlew.bat
└── local.properties
```

---

## Contribuindo

Pull requests são bem-vindos. Para mudanças maiores, abra uma issue primeiro para discutir o que você gostaria de alterar.

---

## Licença

Este projeto está sob a licença MIT. Veja o arquivo `LICENSE` para mais detalhes (adicione se desejar).

---

Feito com ❤️ e Jetpack Compose.
