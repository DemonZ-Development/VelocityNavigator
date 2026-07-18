/*
 * Copyright 2026 DemonZ Development
 * Licensed under the Apache License, Version 2.0.
 */
package com.demonz.velocitynavigator;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class LanguagePacks {

    private static final Set<String> SUPPORTED = Set.of("en", "ru", "es", "fr", "de", "pt_br", "zh_cn");

    private LanguagePacks() {
    }

    public static boolean isSupported(String code) {
        return code != null && SUPPORTED.contains(normalize(code));
    }

    public static Set<String> supportedCodes() {
        return new LinkedHashSet<>(List.of("en", "ru", "es", "fr", "de", "pt_br", "zh_cn"));
    }

    public static LanguageBundle bundle(String code) {
        String normalized = normalize(code);
        LanguageBundle english = LanguageBundle.defaults();
        if ("en".equals(normalized)) {
            return new LanguageBundle("en", "en", english.strings(), english.lists());
        }
        Map<String, String> strings = new LinkedHashMap<>(english.strings());
        Map<String, List<String>> lists = new LinkedHashMap<>(english.lists());
        switch (normalized) {
            case "ru" -> russian(strings, lists);
            case "es" -> spanish(strings, lists);
            case "fr" -> french(strings, lists);
            case "de" -> german(strings, lists);
            case "pt_br" -> portuguese(strings, lists);
            case "zh_cn" -> chinese(strings, lists);
            default -> throw new IllegalArgumentException("Unsupported built-in language: " + code);
        }
        advanced(strings, normalized);
        return new LanguageBundle(normalized, normalized, strings, lists);
    }

    private static String normalize(String code) {
        return code == null ? "" : code.trim().toLowerCase(java.util.Locale.ROOT).replace('-', '_');
    }

    private static void russian(Map<String, String> s, Map<String, List<String>> l) {
        put(s,
                "messages.connecting", "<aqua>Подключаем вас к <server>...</aqua>",
                "messages.already_connected", "<yellow>Вы уже подключены к <server>.</yellow>",
                "messages.no_lobby_found", "<red>Доступное лобби не найдено. (<reason>)</red>",
                "messages.player_only", "<gray>Эту команду может использовать только игрок.</gray>",
                "messages.cooldown", "<yellow>Подождите ещё <time> сек.</yellow>",
                "messages.reload_success", "<green>VelocityNavigator, messages.toml, gui.toml и servers.toml перезагружены.</green>",
                "messages.reload_failed", "<red>Не удалось перезагрузить. Подробности в консоли.</red>",
                "messages.retrying", "<yellow>Повторное подключение... (<attempt>/<max>)</yellow>",
                "messages.permission_denied", "<red>У вас нет прав для этой команды.</red>",
                "messages.route_unavailable", "<red>Сейчас не удалось выбрать лобби.</red>",
                "messages.menu_expired", "<red>Меню лобби устарело. Выполните /<command> ещё раз.</red>",
                "messages.selection_unavailable", "<red>Выбранное лобби больше недоступно.</red>",
                "messages.selection_unregistered", "<red>Выбранный сервер больше не зарегистрирован.</red>",
                "messages.selection_validation_failed", "<red>Не удалось проверить выбранное лобби.</red>",
                "messages.connection_failed", "<red>Не удалось подключиться: <reason></red>",
                "messages.connection_failed_prefix", "<red>Не удалось подключиться: </red>",
                "messages.connection_failed_attempts", "<red>Не удалось подключиться после <attempts> попыток.</red>",
                "messages.connection_error", "<red>Произошла ошибка при подключении к лобби.</red>",
                "messages.unknown_error", "<red>Неизвестная ошибка</red>",
                "menus.status_healthy", "РАБОТАЕТ", "menus.status_draining", "ОБСЛУЖИВАНИЕ",
                "menus.status_open", "НЕДОСТУПЕН", "menus.status_offline", "ВЫКЛЮЧЕН",
                "menus.chat.header", "<gradient:#8EF7FF:#D9F7FF><bold>Выбор лобби</bold></gradient> <gray>(Наведите для статуса, нажмите для подключения)</gray>",
                "menus.chat.entry", "  <gray>•</gray> <white><bold>{server}</bold></white> <gray>| Нажмите для подключения</gray>",
                "menus.chat.tooltip", "<white><bold>{server}</bold></white>\n<gray>Статус:</gray> {status_color}{status}\n<gray>Игроки:</gray> <white>{players}/{max_players}</white>\n<gray>Пинг:</gray> <white>{ping}мс</white>",
                "menus.inventory.title", "<aqua><bold>Выбор главного лобби</bold></aqua>",
                "menus.inventory.bridge_unavailable", "<yellow>Меню-инвентарь недоступно; открыто текстовое меню.</yellow>",
                "menus.inventory.bridge_required", "<red>На этом сервере не установлен GUI-мост.</red>",
                "menus.inventory.previous", "<yellow>Предыдущая страница</yellow>",
                "menus.inventory.next", "<yellow>Следующая страница</yellow>",
                "menus.inventory.refresh", "<aqua>Обновить</aqua>",
                "menus.inventory.page", "<gray>Страница <page>/<pages></gray>",
                "menus.bedrock.title", "<gradient:#8EF7FF:#D9F7FF><bold>Выбор лобби</bold></gradient>",
                "menus.bedrock.content", "<gray>Выберите сервер лобби:</gray>",
                "menus.bedrock.button", "<white><bold>{server}</bold></white> <gray>({players} игроков)</gray>",
                "lobby.no_server_message", "<red>Сейчас нет доступных лобби. Попробуйте позже.</red>",
                "reasons.no_online_lobbies", "Нет доступных серверов лобби.",
                "reasons.selection_unavailable", "Выбранное лобби больше недоступно.",
                "reasons.selection_unregistered", "Выбранный сервер больше не зарегистрирован.",
                "reasons.selection_validation_failed", "Не удалось проверить выбранное лобби."
        );
        l.put("menus.inventory.item_lore", List.of("<gray>Статус:</gray> {status_color}{status}", "<gray>Игроки:</gray> <white>{players}/{max_players}</white>", "<gray>Пинг:</gray> <white>{ping}мс</white>", "", "<yellow>Нажмите для подключения</yellow>"));
        l.put("menus.inventory.unavailable_lore", List.of("<red>Это лобби сейчас недоступно.</red>", "<gray>Нажмите обновить, чтобы проверить снова.</gray>"));
    }

    private static void spanish(Map<String, String> s, Map<String, List<String>> l) {
        common(s, l, "Enviándote a <server>...", "Ya estás conectado a <server>.", "No se encontró ningún lobby disponible. (<reason>)", "Este comando solo puede usarlo un jugador.", "Espera <time> segundo(s).", "Selector de lobby", "Jugadores", "Estado", "Haz clic para conectar", "Anterior", "Siguiente", "Actualizar", "No disponible");
        s.put("messages.permission_denied", "<red>No tienes permiso para usar este comando.</red>");
        s.put("messages.menu_expired", "<red>El menú expiró. Ejecuta /<command> otra vez.</red>");
        s.put("messages.reload_success", "<green>VelocityNavigator, messages.toml, gui.toml y servers.toml recargados.</green>");
        s.put("lobby.no_server_message", "<red>No hay servidores de lobby disponibles. Inténtalo más tarde.</red>");
        put(s,
                "messages.reload_failed", "<red>Error al recargar. Revisa la consola.</red>",
                "messages.retrying", "<yellow>Reintentando la conexión... (<attempt>/<max>)</yellow>",
                "messages.route_unavailable", "<red>No se pudo encontrar un lobby en este momento.</red>",
                "messages.selection_unavailable", "<red>El lobby seleccionado ya no está disponible.</red>",
                "messages.selection_unregistered", "<red>El servidor seleccionado ya no está registrado.</red>",
                "messages.selection_validation_failed", "<red>No se pudo validar el lobby seleccionado.</red>",
                "messages.connection_failed", "<red>Error al conectar: <reason></red>",
                "messages.connection_failed_prefix", "<red>Error al conectar: </red>",
                "messages.connection_failed_attempts", "<red>Error tras <attempts> intento(s).</red>",
                "messages.connection_error", "<red>Ocurrió un error al conectar al lobby.</red>",
                "messages.unknown_error", "<red>Error desconocido</red>",
                "menus.status_healthy", "SALUDABLE", "menus.status_draining", "MANTENIMIENTO",
                "menus.status_open", "BLOQUEADO", "menus.inventory.bridge_unavailable", "<yellow>El inventario no está disponible; se muestra el menú de chat.</yellow>",
                "menus.inventory.bridge_required", "<red>El puente GUI no está instalado en este servidor.</red>",
                "menus.inventory.page", "<gray>Página <page>/<pages></gray>",
                "menus.bedrock.content", "<gray>Selecciona un servidor de lobby:</gray>",
                "menus.bedrock.button", "<white><bold>{server}</bold></white> <gray>({players} jugadores)</gray>",
                "reasons.no_online_lobbies", "No hay servidores de lobby en línea.",
                "reasons.selection_unavailable", "El lobby seleccionado ya no está disponible.",
                "reasons.selection_unregistered", "El servidor seleccionado ya no está registrado.",
                "reasons.selection_validation_failed", "No se pudo validar el lobby seleccionado."
        );
        l.put("menus.inventory.unavailable_lore", List.of("<red>Este lobby no está disponible.</red>", "<gray>Actualiza para comprobar de nuevo.</gray>"));
    }

    private static void french(Map<String, String> s, Map<String, List<String>> l) {
        common(s, l, "Connexion à <server>...", "Vous êtes déjà connecté à <server>.", "Aucun lobby disponible. (<reason>)", "Cette commande est réservée aux joueurs.", "Veuillez patienter encore <time> seconde(s).", "Sélecteur de lobby", "Joueurs", "État", "Cliquez pour vous connecter", "Précédent", "Suivant", "Actualiser", "INDISPONIBLE");
        s.put("messages.permission_denied", "<red>Vous n'avez pas la permission d'utiliser cette commande.</red>");
        s.put("messages.menu_expired", "<red>Le menu a expiré. Relancez /<command>.</red>");
        s.put("messages.reload_success", "<green>VelocityNavigator, messages.toml, gui.toml et servers.toml rechargés.</green>");
        s.put("lobby.no_server_message", "<red>Aucun lobby n'est disponible. Réessayez plus tard.</red>");
        put(s,
                "messages.reload_failed", "<red>Échec du rechargement. Consultez la console.</red>",
                "messages.retrying", "<yellow>Nouvelle tentative... (<attempt>/<max>)</yellow>",
                "messages.route_unavailable", "<red>Impossible de trouver un lobby pour le moment.</red>",
                "messages.selection_unavailable", "<red>Le lobby sélectionné n'est plus disponible.</red>",
                "messages.selection_unregistered", "<red>Le serveur sélectionné n'est plus enregistré.</red>",
                "messages.selection_validation_failed", "<red>Impossible de valider le lobby sélectionné.</red>",
                "messages.connection_failed", "<red>Échec de la connexion : <reason></red>",
                "messages.connection_failed_prefix", "<red>Échec de la connexion : </red>",
                "messages.connection_failed_attempts", "<red>Échec après <attempts> tentative(s).</red>",
                "messages.connection_error", "<red>Une erreur est survenue lors de la connexion au lobby.</red>",
                "messages.unknown_error", "<red>Erreur inconnue</red>",
                "menus.status_healthy", "DISPONIBLE", "menus.status_draining", "MAINTENANCE",
                "menus.status_open", "BLOQUÉ", "menus.inventory.bridge_unavailable", "<yellow>L'inventaire est indisponible ; affichage du menu de discussion.</yellow>",
                "menus.inventory.bridge_required", "<red>Le pont GUI n'est pas installé sur ce serveur.</red>",
                "menus.inventory.page", "<gray>Page <page>/<pages></gray>",
                "menus.bedrock.content", "<gray>Sélectionnez un serveur de lobby :</gray>",
                "menus.bedrock.button", "<white><bold>{server}</bold></white> <gray>({players} joueurs)</gray>",
                "reasons.no_online_lobbies", "Aucun serveur de lobby en ligne.",
                "reasons.selection_unavailable", "Le lobby sélectionné n'est plus disponible.",
                "reasons.selection_unregistered", "Le serveur sélectionné n'est plus enregistré.",
                "reasons.selection_validation_failed", "Impossible de valider le lobby sélectionné."
        );
        l.put("menus.inventory.unavailable_lore", List.of("<red>Ce lobby est indisponible.</red>", "<gray>Actualisez pour vérifier à nouveau.</gray>"));
    }

    private static void german(Map<String, String> s, Map<String, List<String>> l) {
        common(s, l, "Du wirst zu <server> verbunden...", "Du bist bereits mit <server> verbunden.", "Kein verfügbares Lobbyziel gefunden. (<reason>)", "Dieser Befehl kann nur von Spielern verwendet werden.", "Bitte warte noch <time> Sekunde(n).", "Lobby-Auswahl", "Spieler", "Status", "Klicken zum Verbinden", "Zurück", "Weiter", "Aktualisieren", "NICHT VERFÜGBAR");
        s.put("messages.permission_denied", "<red>Du hast keine Berechtigung für diesen Befehl.</red>");
        s.put("messages.menu_expired", "<red>Das Menü ist abgelaufen. Führe /<command> erneut aus.</red>");
        s.put("messages.reload_success", "<green>VelocityNavigator, messages.toml, gui.toml und servers.toml wurden neu geladen.</green>");
        s.put("lobby.no_server_message", "<red>Derzeit ist keine Lobby verfügbar. Versuche es später erneut.</red>");
        put(s,
                "messages.reload_failed", "<red>Neuladen fehlgeschlagen. Prüfe die Konsole.</red>",
                "messages.retrying", "<yellow>Verbindung wird erneut versucht... (<attempt>/<max>)</yellow>",
                "messages.route_unavailable", "<red>Derzeit konnte keine Lobby gefunden werden.</red>",
                "messages.selection_unavailable", "<red>Die gewählte Lobby ist nicht mehr verfügbar.</red>",
                "messages.selection_unregistered", "<red>Der gewählte Server ist nicht mehr registriert.</red>",
                "messages.selection_validation_failed", "<red>Die gewählte Lobby konnte nicht geprüft werden.</red>",
                "messages.connection_failed", "<red>Verbindung fehlgeschlagen: <reason></red>",
                "messages.connection_failed_prefix", "<red>Verbindung fehlgeschlagen: </red>",
                "messages.connection_failed_attempts", "<red>Verbindung nach <attempts> Versuch(en) fehlgeschlagen.</red>",
                "messages.connection_error", "<red>Beim Verbinden mit der Lobby ist ein Fehler aufgetreten.</red>",
                "messages.unknown_error", "<red>Unbekannter Fehler</red>",
                "menus.status_healthy", "VERFÜGBAR", "menus.status_draining", "WARTUNG",
                "menus.status_open", "GESPERRT", "menus.inventory.bridge_unavailable", "<yellow>Inventarmenü nicht verfügbar; Chatmenü wird angezeigt.</yellow>",
                "menus.inventory.bridge_required", "<red>Die GUI-Bridge ist auf diesem Server nicht installiert.</red>",
                "menus.inventory.page", "<gray>Seite <page>/<pages></gray>",
                "menus.bedrock.content", "<gray>Wähle einen Lobbyserver:</gray>",
                "menus.bedrock.button", "<white><bold>{server}</bold></white> <gray>({players} Spieler)</gray>",
                "reasons.no_online_lobbies", "Keine Lobbyserver online.",
                "reasons.selection_unavailable", "Die gewählte Lobby ist nicht mehr verfügbar.",
                "reasons.selection_unregistered", "Der gewählte Server ist nicht mehr registriert.",
                "reasons.selection_validation_failed", "Die gewählte Lobby konnte nicht geprüft werden."
        );
        l.put("menus.inventory.unavailable_lore", List.of("<red>Diese Lobby ist derzeit nicht verfügbar.</red>", "<gray>Aktualisiere die Ansicht, um erneut zu prüfen.</gray>"));
    }

    private static void portuguese(Map<String, String> s, Map<String, List<String>> l) {
        common(s, l, "Enviando você para <server>...", "Você já está conectado ao <server>.", "Nenhum lobby disponível foi encontrado. (<reason>)", "Este comando só pode ser usado por um jogador.", "Aguarde mais <time> segundo(s).", "Seletor de lobby", "Jogadores", "Status", "Clique para conectar", "Anterior", "Próxima", "Atualizar", "INDISPONÍVEL");
        s.put("messages.permission_denied", "<red>Você não tem permissão para usar este comando.</red>");
        s.put("messages.menu_expired", "<red>O menu expirou. Execute /<command> novamente.</red>");
        s.put("messages.reload_success", "<green>VelocityNavigator, messages.toml, gui.toml e servers.toml recarregados.</green>");
        s.put("lobby.no_server_message", "<red>Nenhum lobby está disponível agora. Tente novamente mais tarde.</red>");
        put(s,
                "messages.reload_failed", "<red>Falha ao recarregar. Verifique o console.</red>",
                "messages.retrying", "<yellow>Tentando conectar novamente... (<attempt>/<max>)</yellow>",
                "messages.route_unavailable", "<red>Não foi possível encontrar um lobby agora.</red>",
                "messages.selection_unavailable", "<red>O lobby selecionado não está mais disponível.</red>",
                "messages.selection_unregistered", "<red>O servidor selecionado não está mais registrado.</red>",
                "messages.selection_validation_failed", "<red>Não foi possível validar o lobby selecionado.</red>",
                "messages.connection_failed", "<red>Falha ao conectar: <reason></red>",
                "messages.connection_failed_prefix", "<red>Falha ao conectar: </red>",
                "messages.connection_failed_attempts", "<red>Falha após <attempts> tentativa(s).</red>",
                "messages.connection_error", "<red>Ocorreu um erro ao conectar ao lobby.</red>",
                "messages.unknown_error", "<red>Erro desconhecido</red>",
                "menus.status_healthy", "SAUDÁVEL", "menus.status_draining", "MANUTENÇÃO",
                "menus.status_open", "BLOQUEADO", "menus.inventory.bridge_unavailable", "<yellow>Inventário indisponível; exibindo o menu de chat.</yellow>",
                "menus.inventory.bridge_required", "<red>A ponte GUI não está instalada neste servidor.</red>",
                "menus.inventory.page", "<gray>Página <page>/<pages></gray>",
                "menus.bedrock.content", "<gray>Selecione um servidor de lobby:</gray>",
                "menus.bedrock.button", "<white><bold>{server}</bold></white> <gray>({players} jogadores)</gray>",
                "reasons.no_online_lobbies", "Nenhum servidor de lobby está online.",
                "reasons.selection_unavailable", "O lobby selecionado não está mais disponível.",
                "reasons.selection_unregistered", "O servidor selecionado não está mais registrado.",
                "reasons.selection_validation_failed", "Não foi possível validar o lobby selecionado."
        );
        l.put("menus.inventory.unavailable_lore", List.of("<red>Este lobby está indisponível.</red>", "<gray>Atualize para verificar novamente.</gray>"));
    }

    private static void chinese(Map<String, String> s, Map<String, List<String>> l) {
        common(s, l, "正在将你传送到 <server>...", "你已经连接到 <server>。", "找不到可用大厅。(<reason>)", "此命令只能由玩家使用。", "请再等待 <time> 秒。", "大厅选择器", "玩家", "状态", "点击连接", "上一页", "下一页", "刷新", "不可用");
        s.put("messages.permission_denied", "<red>你没有权限使用此命令。</red>");
        s.put("messages.menu_expired", "<red>大厅菜单已过期，请再次运行 /<command>。</red>");
        s.put("messages.reload_success", "<green>VelocityNavigator、messages.toml、gui.toml 和 servers.toml 已重新加载。</green>");
        s.put("lobby.no_server_message", "<red>当前没有可用大厅，请稍后再试。</red>");
        put(s,
                "messages.reload_failed", "<red>重新加载失败，请检查控制台。</red>",
                "messages.retrying", "<yellow>正在重试连接... (<attempt>/<max>)</yellow>",
                "messages.route_unavailable", "<red>当前无法选择大厅。</red>",
                "messages.selection_unavailable", "<red>所选大厅已不可用。</red>",
                "messages.selection_unregistered", "<red>所选服务器已不再注册。</red>",
                "messages.selection_validation_failed", "<red>无法验证所选大厅。</red>",
                "messages.connection_failed", "<red>连接失败：<reason></red>",
                "messages.connection_failed_prefix", "<red>连接失败：</red>",
                "messages.connection_failed_attempts", "<red>尝试 <attempts> 次后仍连接失败。</red>",
                "messages.connection_error", "<red>连接大厅时发生错误。</red>",
                "messages.unknown_error", "<red>未知错误</red>",
                "menus.status_healthy", "正常", "menus.status_draining", "维护中",
                "menus.status_open", "已阻止", "menus.inventory.bridge_unavailable", "<yellow>物品栏菜单不可用，改用聊天菜单。</yellow>",
                "menus.inventory.bridge_required", "<red>此后端服务器未安装 GUI 桥接。</red>",
                "menus.inventory.page", "<gray>第 <page>/<pages> 页</gray>",
                "menus.bedrock.content", "<gray>请选择大厅服务器：</gray>",
                "menus.bedrock.button", "<white><bold>{server}</bold></white> <gray>（{players} 名玩家）</gray>",
                "reasons.no_online_lobbies", "没有在线大厅服务器。",
                "reasons.selection_unavailable", "所选大厅已不可用。",
                "reasons.selection_unregistered", "所选服务器已不再注册。",
                "reasons.selection_validation_failed", "无法验证所选大厅。"
        );
        l.put("menus.inventory.unavailable_lore", List.of("<red>此大厅当前不可用。</red>", "<gray>请刷新后重试。</gray>"));
    }

    private static void common(Map<String, String> s, Map<String, List<String>> l, String connecting, String connected, String missing, String playerOnly,
                               String cooldown, String selector, String players, String status, String click,
                               String previous, String next, String refresh, String unavailable) {
        s.put("messages.connecting", "<aqua>" + connecting + "</aqua>");
        s.put("messages.already_connected", "<yellow>" + connected + "</yellow>");
        s.put("messages.no_lobby_found", "<red>" + missing + "</red>");
        s.put("messages.player_only", "<gray>" + playerOnly + "</gray>");
        s.put("messages.cooldown", "<yellow>" + cooldown + "</yellow>");
        s.put("menus.status_offline", unavailable);
        s.put("menus.chat.header", "<gradient:#8EF7FF:#D9F7FF><bold>" + selector + "</bold></gradient>");
        s.put("menus.chat.entry", "  <gray>•</gray> <white><bold>{server}</bold></white> <gray>| " + click + "</gray>");
        s.put("menus.chat.tooltip", "<white><bold>{server}</bold></white>\n<gray>" + status + ":</gray> {status_color}{status}\n<gray>" + players + ":</gray> <white>{players}/{max_players}</white>\n<gray>Ping:</gray> <white>{ping}ms</white>");
        s.put("menus.inventory.title", "<aqua><bold>" + selector + "</bold></aqua>");
        s.put("menus.bedrock.title", "<gradient:#8EF7FF:#D9F7FF><bold>" + selector + "</bold></gradient>");
        s.put("menus.inventory.previous", "<yellow>" + previous + "</yellow>");
        s.put("menus.inventory.next", "<yellow>" + next + "</yellow>");
        s.put("menus.inventory.refresh", "<aqua>" + refresh + "</aqua>");
        l.put("menus.inventory.item_lore", List.of("<gray>" + status + ":</gray> {status_color}{status}", "<gray>" + players + ":</gray> <white>{players}/{max_players}</white>", "<gray>Ping:</gray> <white>{ping}ms</white>", "", "<yellow>" + click + "</yellow>"));
    }

    private static void advanced(Map<String, String> s, String language) {
        String[] selectorStates = switch (language) {
            case "ru" -> new String[]{"\u0417\u0410\u041f\u041e\u041b\u041d\u0415\u041d", "\u0412 \u0418\u0413\u0420\u0415"};
            case "es" -> new String[]{"LLENO", "EN JUEGO"};
            case "fr" -> new String[]{"COMPLET", "EN JEU"};
            case "de" -> new String[]{"VOLL", "IM SPIEL"};
            case "pt_br" -> new String[]{"LOTADO", "EM JOGO"};
            case "zh_cn" -> new String[]{"\u5df2\u6ee1", "\u6e38\u620f\u4e2d"};
            default -> new String[]{"FULL", "IN GAME"};
        };
        s.put("menus.status_full", selectorStates[0]);
        s.put("menus.status_in_game", selectorStates[1]);
        String[] values = switch (language) {
            case "ru" -> new String[]{"Система групп отключена.", "Вы пригласили <target> в группу.", "<player> приглашает вас в группу. Используйте /party accept или /party deny.", "Вы присоединились к группе.", "Вы не состоите в группе.", "Только лидер группы может это сделать.", "[Группа] <player>: <message>", "Место в очереди: <position>/<size>", "Пулы лобби заполнены. Вы добавлены в очередь на позицию <position>.", "Освободилось место. Подключение к <server>...", "Вы покинули очередь.", "Сейчас вы не в очереди."};
            case "es" -> new String[]{"El sistema de grupos está desactivado.", "Invitaste a <target> a tu grupo.", "<player> te invitó a un grupo. Usa /party accept o /party deny.", "Te uniste al grupo.", "No estás en un grupo.", "Solo el líder del grupo puede hacer eso.", "[Grupo] <player>: <message>", "Posición en la cola: <position>/<size>", "Los lobbies están llenos. Entraste en la cola en la posición <position>.", "Hay un espacio libre. Conectando a <server>...", "Saliste de la cola.", "No estás en la cola."};
            case "fr" -> new String[]{"Le système de groupe est désactivé.", "Vous avez invité <target> dans votre groupe.", "<player> vous invite dans un groupe. Utilisez /party accept ou /party deny.", "Vous avez rejoint le groupe.", "Vous n'êtes pas dans un groupe.", "Seul le chef du groupe peut faire cela.", "[Groupe] <player> : <message>", "Position dans la file : <position>/<size>", "Les lobbies sont pleins. Vous êtes en position <position>.", "Une place est libre. Connexion à <server>...", "Vous avez quitté la file.", "Vous n'êtes pas dans la file."};
            case "de" -> new String[]{"Das Gruppensystem ist deaktiviert.", "Du hast <target> in deine Gruppe eingeladen.", "<player> hat dich eingeladen. Nutze /party accept oder /party deny.", "Du bist der Gruppe beigetreten.", "Du bist in keiner Gruppe.", "Nur der Gruppenleiter darf das.", "[Gruppe] <player>: <message>", "Warteschlangenposition: <position>/<size>", "Die Lobbys sind voll. Du bist auf Position <position>.", "Ein Platz ist frei. Verbindung mit <server>...", "Du hast die Warteschlange verlassen.", "Du bist nicht in der Warteschlange."};
            case "pt_br" -> new String[]{"O sistema de grupos está desativado.", "Você convidou <target> para o grupo.", "<player> convidou você. Use /party accept ou /party deny.", "Você entrou no grupo.", "Você não está em um grupo.", "Somente o líder do grupo pode fazer isso.", "[Grupo] <player>: <message>", "Posição na fila: <position>/<size>", "Os lobbies estão lotados. Você entrou na posição <position>.", "Uma vaga abriu. Conectando a <server>...", "Você saiu da fila.", "Você não está na fila."};
            case "zh_cn" -> new String[]{"队伍系统已禁用。", "你已邀请 <target> 加入队伍。", "<player> 邀请你加入队伍。使用 /party accept 或 /party deny。", "你已加入队伍。", "你不在队伍中。", "只有队长可以这样做。", "[队伍] <player>: <message>", "队列位置：<position>/<size>", "大厅已满。你已进入队列，位置 <position>。", "已有空位，正在连接到 <server>...", "你已离开队列。", "你当前不在队列中。"};
            default -> null;
        };
        if (values == null) return;
        String bedrockUnavailable = switch (language) {
            case "ru" -> "Форма выбора лобби Bedrock недоступна.";
            case "es" -> "El formulario de lobby de Bedrock no está disponible.";
            case "fr" -> "Le formulaire de lobby Bedrock est indisponible.";
            case "de" -> "Das Bedrock-Lobbyformular ist nicht verfügbar.";
            case "pt_br" -> "O formulário de lobby Bedrock está indisponível.";
            case "zh_cn" -> "Bedrock 大厅表单不可用。";
            default -> "The Bedrock lobby form is unavailable.";
        };
        s.put("menus.bedrock.unavailable", "<red>" + bedrockUnavailable + "</red>");
        String[] extras = switch (language) {
            case "ru" -> new String[]{"Используйте /party invite <player>, accept, deny, kick <player>, leave или disband.", "Игрок <target> не найден.", "У вас нет действительного приглашения.", "<player> присоединился к группе.", "Приглашение отклонено.", "<target> удалён из группы.", "Вас удалили из группы.", "Группа распущена.", "Группа (<count>): <members>", "Нельзя выбрать себя.", "Этот игрок уже в группе.", "Этот игрок не в вашей группе.", "Лидер должен распустить группу.", "Группа обновлена.", "Используйте /p <message>.", "Группа достигла лимита участников.", "Очередь лобби заполнена."};
            case "es" -> new String[]{"Usa /party invite <player>, accept, deny, kick <player>, leave o disband.", "No se encontró a <target>.", "No tienes una invitación válida.", "<player> se unió al grupo.", "Invitación rechazada.", "Se eliminó a <target> del grupo.", "Fuiste expulsado del grupo.", "El grupo fue disuelto.", "Grupo (<count>): <members>", "No puedes seleccionarte.", "Ese jugador ya está en un grupo.", "Ese jugador no está en tu grupo.", "El líder debe disolver el grupo.", "Grupo actualizado.", "Usa /p <message>.", "El grupo alcanzó su límite.", "La cola de lobbies está llena."};
            case "fr" -> new String[]{"Utilisez /party invite <player>, accept, deny, kick <player>, leave ou disband.", "Joueur <target> introuvable.", "Vous n'avez aucune invitation valide.", "<player> a rejoint le groupe.", "Invitation refusée.", "<target> a été retiré du groupe.", "Vous avez été retiré du groupe.", "Le groupe a été dissous.", "Groupe (<count>) : <members>", "Vous ne pouvez pas vous cibler.", "Ce joueur appartient déjà à un groupe.", "Ce joueur n'est pas dans votre groupe.", "Le chef doit dissoudre le groupe.", "Groupe mis à jour.", "Utilisez /p <message>.", "Le groupe a atteint sa limite.", "La file des lobbies est pleine."};
            case "de" -> new String[]{"Nutze /party invite <player>, accept, deny, kick <player>, leave oder disband.", "Spieler <target> wurde nicht gefunden.", "Du hast keine gültige Einladung.", "<player> ist der Gruppe beigetreten.", "Einladung abgelehnt.", "<target> wurde aus der Gruppe entfernt.", "Du wurdest aus der Gruppe entfernt.", "Die Gruppe wurde aufgelöst.", "Gruppe (<count>): <members>", "Du kannst dich nicht selbst auswählen.", "Dieser Spieler ist bereits in einer Gruppe.", "Dieser Spieler ist nicht in deiner Gruppe.", "Der Leiter muss die Gruppe auflösen.", "Gruppe aktualisiert.", "Nutze /p <message>.", "Die Gruppe hat ihr Limit erreicht.", "Die Lobby-Warteschlange ist voll."};
            case "pt_br" -> new String[]{"Use /party invite <player>, accept, deny, kick <player>, leave ou disband.", "Jogador <target> não encontrado.", "Você não tem um convite válido.", "<player> entrou no grupo.", "Convite recusado.", "<target> foi removido do grupo.", "Você foi removido do grupo.", "O grupo foi desfeito.", "Grupo (<count>): <members>", "Você não pode escolher a si mesmo.", "Esse jogador já está em um grupo.", "Esse jogador não está no seu grupo.", "O líder deve desfazer o grupo.", "Grupo atualizado.", "Use /p <message>.", "O grupo atingiu o limite.", "A fila de lobbies está cheia."};
            case "zh_cn" -> new String[]{"使用 /party invite <player>、accept、deny、kick <player>、leave 或 disband。", "未找到玩家 <target>。", "你没有有效的队伍邀请。", "<player> 加入了队伍。", "已拒绝邀请。", "已将 <target> 移出队伍。", "你已被移出队伍。", "队伍已解散。", "队伍（<count>）：<members>", "不能选择自己。", "该玩家已经在一个队伍中。", "该玩家不在你的队伍中。", "队长必须解散队伍。", "队伍已更新。", "使用 /p <message>。", "队伍人数已达上限。", "大厅队列已满。"};
            default -> null;
        };
        if (extras != null) put(s,
                "party.usage", "<yellow>" + extras[0].replace("<player>", "&lt;player&gt;") + "</yellow>", "party.player_not_found", "<red>" + extras[1] + "</red>",
                "party.no_invite", "<red>" + extras[2] + "</red>", "party.member_joined", "<green>" + extras[3] + "</green>",
                "party.invite_denied", "<gray>" + extras[4] + "</gray>", "party.kicked", "<yellow>" + extras[5] + "</yellow>",
                "party.you_were_kicked", "<red>" + extras[6] + "</red>", "party.disbanded", "<yellow>" + extras[7] + "</yellow>",
                "party.status", "<aqua>" + extras[8] + "</aqua>", "party.self", "<red>" + extras[9] + "</red>",
                "party.already_in_party", "<red>" + extras[10] + "</red>", "party.not_member", "<red>" + extras[11] + "</red>",
                "party.leader_must_disband", "<red>" + extras[12] + "</red>", "party.done", "<green>" + extras[13] + "</green>",
                "party.chat_usage", "<yellow>" + extras[14].replace("<message>", "&lt;message&gt;") + "</yellow>", "party.full", "<red>" + extras[15] + "</red>",
                "queue.full", "<red>" + extras[16] + "</red>"
        );
        put(s,
                "party.disabled", "<red>" + values[0] + "</red>",
                "party.invite_sent", "<green>" + values[1] + "</green>",
                "party.invite_received", "<yellow>" + values[2] + "</yellow>",
                "party.joined", "<green>" + values[3] + "</green>",
                "party.not_in_party", "<red>" + values[4] + "</red>",
                "party.not_leader", "<red>" + values[5] + "</red>",
                "party.chat", "<light_purple>" + values[6] + "</light_purple>",
                "queue.position", "<yellow>" + values[7] + "</yellow>",
                "queue.joined", "<green>" + values[8] + "</green>",
                "queue.connecting", "<green>" + values[9] + "</green>",
                "queue.left", "<yellow>" + values[10] + "</yellow>",
                "queue.not_queued", "<gray>" + values[11] + "</gray>"
        );
    }

    private static void put(Map<String, String> target, String... values) {
        for (int index = 0; index < values.length; index += 2) {
            target.put(values[index], values[index + 1]);
        }
    }
}
