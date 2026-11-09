<div align="center">
  <img width="2048" height="989" alt="17831909936168749184217530060427" src="https://github.com/user-attachments/assets/7a1b7c4d-3610-4541-b400-8a8c7ef91605" />
<h1><img src="https://github.com/user-attachments/assets/02086040-660d-42d8-8830-78053e1e05a4" height="30" style="vertical-align: middle;"> Kizzy Enhanced</h1>
<h4>Um fork aprimorado do <a href="https://github.com/dead8309/Kizzy">Kizzy</a> — um gerenciador de Rich Presence do Discord para Android, escrito em Kotlin.</h4>
</div>

<div align="center">
<img src="https://img.shields.io/badge/Minimum%20SDK-27-%23?&style=flat-square&color=5b5ef7">
<img src="https://img.shields.io/badge/kotlin-5b5ef7.svg?logo=kotlin&logoColor=white&style=flat-square">
<img src="https://img.shields.io/badge/package-com.my.kizzy.enhanced-5b5ef7?style=flat-square">
<img src="https://img.shields.io/badge/name-Kizzy%20Enhanced-5b5ef7?style=flat-square">
<div align="center">

[![README in English](https://img.shields.io/badge/README-in%20English-blue?style=for-the-badge)](README.md)
[![README em português](https://img.shields.io/badge/README-em%20português-blue?style=for-the-badge)](README_PT.md)

</div>
</div>
<p>
<img src="https://github.com/user-attachments/assets/f8402300-d628-4104-8f86-dc6021c9fbbe" width=60%/>
</p>
</div>

> [!NOTE]
> **Kizzy Enhanced** é um fork pessoal do [Kizzy](https://github.com/dead8309/Kizzy) por dead8309. As adições específicas do fork estão marcadas com ⭐ abaixo.

## Recursos

- [x] Botões clicáveis
- [x] Detecta o aplicativo em execução atual
- [x] Detecta a mídia reproduzida atualmente
- [x] Carimbos de data/hora opcionais
- [x] Status personalizado
- [x] Salvar/carregar configurações de presença
- [x] Tema Material You
- [x] Traduções
- [x] Configuração simples
- [x] Spoof de Console RPC
- [x] Criar configurações personalizadas com suas próprias imagens e links
- [x] Visualizar RPC no próprio aplicativo
- [x] Executa em segundo plano mesmo com a tela desligada
- [x] Suporte a GIF
- [x] Suporte a URL externa (significa que você pode fornecer um URL que aponte para uma imagem na web e o Discord a exibirá!)
- [x] Usar imagens da galeria
- [x] ⭐ Spoofer de plataforma (Desktop / Web / Android / iOS / Xbox / Playstation / VR)
- [x] ⭐ ID de Aplicativo Personalizado — use seu próprio ícone e nome de aplicativo do Discord
- [x] ⭐ Placeholders de modelo adicionais (`{{media_album}}`, `{{battery}}`, `{{playback}}` e mais)
- [x] ⭐ Seletor de modo de timestamp (Mídia/App padrão · Hora atual do dia · ms personalizados)
- [x] ⭐ Redesenho da tela inicial — cartões de largura total com alternadores de ligar/desligar
- [x] ⭐ Reconexão automática do gateway com backoff exponencial (sem mais quedas após 1 a 2 horas)
- [x] ⭐ Loop de detecção indestrutível — erros de rede não interrompem mais a detecção de aplicativos/jogos
- [x] ⭐ Sondagem de troca de aplicativos de 2 segundos (era 5s) para detecção de jogos mais rápida

## O que há de novo neste fork

Todas as adições estão na tela **Experimental RPC** e na tela inicial.

### ⭐ Spoofer de plataforma
- Escolha de qual plataforma sua presença se conecta — **Desktop, Web, Android, iOS, Xbox, Playstation, VR** (portado das plataformas usadas pelo platformSpoofer do Equicord).
- Funciona no nível `IDENTIFY` do gateway (status do cliente), para que seja detectado corretamente por plugins como **platformindicators**.
- É aplicado **ao vivo** — o serviço é reiniciado automaticamente, sem necessidade de alternância manual.

### ⭐ ID de Aplicativo Personalizado (ícone próprio)
- Seletor **Built-in / Custom**. O Built-in mantém o ícone padrão; o Custom revela um campo onde você cola seu próprio **Application ID** do Discord, para que a atividade mostre **seu** ícone e nome.

### ⭐ Placeholders de modelo adicionais
Além dos originais, com preenchimento automático:
- `{{media_album}}`, `{{media_album_artist}}`, `{{media_duration}}`, `{{media_writer}}`, `{{media_composer}}`
- `{{battery}}` — porcentagem da bateria
- `{{playback}}` — estado de reprodução (▶ / ⏸)

### ⭐ Seletor de modo de timestamp
- **Mídia / App (padrão)** — timestamps da faixa/app atual.
- **Igual à hora atual do dia** — O Discord mostra a hora atual como decorrida (por exemplo, às 15:30 → mostra "15:30 decorridos"). Mesma lógica do customRPC do Vencord.
- **Personalizado** — digite manualmente os **Timestamps de Início / Fim (em milissegundos)**.

### ⭐ Redesenho da tela inicial
- Os cartões de recursos agora são uma pilha vertical de cartões de largura total.
- **Interruptores de ligar/desligar em cada cartão**, para que Mídia / Custom / Experimental RPC possam ser iniciados e parados diretamente da tela inicial.

### ⭐ Resiliência do Gateway (portado de [YuuzuTH/Kizzy](https://github.com/YuuzuTH/Kizzy))
- Reconexão automática com backoff exponencial em qualquer desconexão inesperada.
- Retoma a sessão quando possível; retorna para um identify novo após 3 tentativas falhas.
- Códigos de fechamento fatais (4004, 4010–4014) ainda param permanentemente.
- Limite de 60 segundos nas esperas de atualização de presença (era um loop infinito).
- O loop de detecção agora está envolvido em try/catch — um único erro de rede não interrompe mais a detecção de troca de aplicativos até a reinicialização manual.
- Sondagem de troca de aplicativos: **2s** (era 5s).
- Correção: alternar entre dois aplicativos habilitados agora atualiza a presença imediatamente.

### Correções
- Corrigido travamento ao **"Compartilhar registro"** (a autoridade do FileProvider agora segue o ID do pacote).
- Corrigidos travamentos ao alternar recursos RPC na tela inicial.

## Requisitos do Sistema
- SO: Android 8.1 a 16
- RAM: 3 GB mínimo

## Download
Obtenha o `app-release.apk` assinado mais recente na página de [**Releases**](../../releases/latest).

O **Kizzy Enhanced** usa o ID de pacote **`com.my.kizzy.enhanced`**, permitindo a instalação junto ao Kizzy original, caso você o possua.

> [!WARNING]
> Este aplicativo usa a conexão do Gateway do Discord. Use por sua conta e risco.

## Compilação
> Pré-requisitos: Android Studio e familiaridade com Gradle, Kotlin e Jetpack Compose.

```console
git clone https://github.com/himyjan/Kizzy-Enhanced.git
```
Abra o projeto no Android Studio, importe-o e faça o Build and Run. As compilações de lançamento são produzidas automaticamente pelo fluxo de trabalho do GitHub Actions **Build Release Apk**.

## Créditos
✨ [Kizzy](https://github.com/dead8309/Kizzy) pelo projeto Kizzy original.

✨ [YuuzuTH/Kizzy](https://github.com/YuuzuTH/Kizzy) pelas correções de resiliência de gateway.

✨ [Read You](https://github.com/Ashinch/ReadYou) e [Seal](https://github.com/JunkFood02/Seal) para Componentes de IU.

✨ [Material Color Utilities](https://github.com/material-foundation/material-color-utilities)

✨ [Rich-Presence-U](https://github.com/ninstar/Rich-Presence-U) para dados de jogos do Nintendo e Wii U.

✨ [Logra](https://github.com/wingio/Logra) para a IU de logs.

✨ [Xbox-Rich-Presence-Discord](https://github.com/MrCoolAndroid/Xbox-Rich-Presence-Discord) para dados de jogos do Xbox.

✨ [Monet](https://github.com/Kyant0/Monet) para paletas do Material3.

## Licença
O **Kizzy** é um projeto de código aberto sob a Licença Pública Geral GNU (GNU GPL 3.0), permitindo usar, referenciar e modificar o código-fonte gratuitamente, mas sem permitir que o código modificado e derivado seja distribuído e vendido como software comercial de código fechado.
