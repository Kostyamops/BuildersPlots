# JouTak Plugin Template (Kotlin)
Заготовка для плагина на JouTak (ДжойТек) (<ins>joutak.ru-mc.ru</ins>)

# Перед использованием

1. Отредактируйте:
    - `settings.gradle.kts` &mdash; укажите название проекта
    - `gradle.properties` &mdash; укажите версию плагина, commitHash оставьте пустым
    - `src/main/kotlin/com/joutak/` &mdash; переименуйте под название вашего проекта
    - `src/main/resources/plugin.yml` &mdash; укажите правильный путь до класса плагина
    - `.run/build_snapshot_run.xml` &mdash; расскоментируйте и укажите путь до сервера в ключе SERVER_PATH
   

Что нужно сделать:
1) склонить проект (форк) себе по ssh 
2) открыть в любой удобной ide(vs code | inteliji idea)
3) разобраться с расположением папочек и синтаксисом котлина, прописать базовую логику, которая описана в issues. (креативность поощрается)
4) собрать его с помощью gradle 
5) запустить у себя на компе Purpur сервер и закинуть на него этот плагин
6) подключиться со своего майна, проверить, что оно заработало
7) отправить пулл реквест обратно на гитхаб, закрыв issue
8) пингануть кого-нибудь, чтобы реквест посмотрели и приняли :)

если всё работает, то пулл реквест автоматически сбилдит такой же jarник, который будет так же работать на твоём локальном сервере.

сначала всё можно писать в main файлик, но потом команды и лисенеры ивентов выдели в два отдельных файлика. Главное - не стесняйся спрашивать!

# Полезное:
## Building JAR

1. Add a project from repo into Intellij IDEA
2. Use "Run" button on the top right to build jar\
`[snapshot]` builds go to `plugins/` folder of test server \
`[release]` builds go to `build/` folder of the project
