# e-Mola Gestor — versão 1.1

Aplicação Android Kotlin que processa automaticamente novos SMS do remetente `eMola` após consentimento para `RECEIVE_SMS`.

## Relatórios
- Diário: valores recebidos, enviados e diferença, por dia seleccionado.
- Mensal: os mesmos indicadores por mês e ano.
- Anual: os mesmos indicadores por ano.
- Navegação pelos períodos com as setas, selecção de data no calendário e lista de movimentos filtrados.
- O valor **Diferença** é recebido menos enviado, não o saldo e-Mola.

## Formato reconhecido
Mensagem exemplo: `ID da transacao PP261023.1740.w79225. Transferiste 1,500.00MT para conta ... as 17:40:30 de 23/10/2026.`
O valor principal é 1500 MT, do tipo Enviado. Não contabiliza o saldo ou a taxa como transferência. Usa a data e hora do SMS, quando disponíveis, e caso contrário a data de recepção.

O parser também reconhece `Recebeste <valor>MT` com ID de transacção, mas é necessário testar com uma mensagem real recebida para validar o formato e possíveis variantes.

## Instalar
1. Abrir o projecto no Android Studio com Android SDK 35 e JDK 17.
2. Sincronizar Gradle e, se solicitado, seleccionar versões compatíveis dos plugins Android e Kotlin.
3. Menu **Build > Build Bundle(s) / APK(s) > Build APK(s)**.
4. Instalar `app/build/outputs/apk/debug/app-debug.apk` no Android 8.0 ou superior.
5. Abrir e conceder permissão de recepção de SMS.

## Limitações
- Apenas novos SMS após autorização; não lê mensagens antigas.
- SMS multipartes são agrupados na recepção; formatos diferentes precisam de validação.
- Dados locais em SharedPreferences, sem Internet, sem cópia de segurança; desinstalar elimina o histórico.
- A publicação na Play Store está sujeita às restrições de permissões SMS da Google.
- Projecto fornecido como código-fonte, sem APK compilado ou testes em dispositivo real.
