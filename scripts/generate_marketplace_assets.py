from pathlib import Path
from PIL import Image, ImageDraw, ImageFont


ROOT = Path(__file__).resolve().parents[1]
ASSETS = ROOT / "assets"
WIKI = ROOT / "wiki"
MARKETPLACE = ASSETS / "marketplace"
WIKI_HEADERS = WIKI / "headers"

NAVY = (7, 15, 29)
NAVY_2 = (11, 24, 42)
SURFACE = (15, 31, 52)
CYAN = (55, 214, 224)
CYAN_SOFT = (135, 235, 240)
WHITE = (242, 247, 250)
MUTED = (154, 172, 190)
LINE = (42, 66, 91)
GREEN = (72, 210, 151)


def first_font(paths):
    for value in paths:
        path = Path(value)
        if path.exists():
            return str(path)
    raise FileNotFoundError(paths[0])


REGULAR = first_font(["C:/Windows/Fonts/segoeui.ttf", "C:/Windows/Fonts/arial.ttf"])
SEMIBOLD = first_font(["C:/Windows/Fonts/seguisb.ttf", "C:/Windows/Fonts/arialbd.ttf"])
BOLD = first_font(["C:/Windows/Fonts/segoeuib.ttf", "C:/Windows/Fonts/arialbd.ttf"])


def font(size, weight="regular"):
    path = REGULAR
    if weight == "semibold":
        path = SEMIBOLD
    if weight == "bold":
        path = BOLD
    return ImageFont.truetype(path, size)


def blend(a, b, amount):
    return tuple(round(a[i] + (b[i] - a[i]) * amount) for i in range(3))


def canvas(size):
    width, height = size
    image = Image.new("RGB", size, NAVY)
    draw = ImageDraw.Draw(image)
    for y in range(height):
        draw.line((0, y, width, y), fill=blend(NAVY_2, NAVY, y / max(1, height - 1)))
    return image.convert("RGBA")


def mark(size, color=CYAN):
    image = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    draw = ImageDraw.Draw(image)
    scale = size / 1024
    ring = (180 * scale, 180 * scale, 844 * scale, 844 * scale)
    ring_width = max(7, round(15 * scale))
    draw.arc(ring, 205, 335, fill=(*color, 255), width=ring_width)
    draw.arc(ring, 25, 155, fill=(*color, 255), width=ring_width)
    width = max(22, round(58 * scale))
    left = [(300 * scale, 310 * scale), (512 * scale, 724 * scale)]
    right = [(512 * scale, 724 * scale), (724 * scale, 310 * scale)]
    draw.line(left, fill=(*color, 255), width=width)
    draw.line(right, fill=(*CYAN_SOFT, 255), width=width)
    draw.polygon([
        (724 * scale, 238 * scale),
        (650 * scale, 334 * scale),
        (759 * scale, 346 * scale),
    ], fill=(*CYAN_SOFT, 255))
    draw.ellipse((266 * scale, 276 * scale, 334 * scale, 344 * scale), fill=(*NAVY, 255), outline=(*color, 255), width=max(5, round(12 * scale)))
    draw.ellipse((284 * scale, 294 * scale, 316 * scale, 326 * scale), fill=(*color, 255))
    draw.polygon([
        (512 * scale, 666 * scale),
        (570 * scale, 724 * scale),
        (512 * scale, 782 * scale),
        (454 * scale, 724 * scale),
    ], fill=(*WHITE, 255))
    return image


def save(image, path):
    path.parent.mkdir(parents=True, exist_ok=True)
    image.save(path, "PNG", optimize=True, compress_level=9)


def plugin_icon():
    image = canvas((1024, 1024))
    image.alpha_composite(mark(760), (140, 132))
    save(image.convert("RGB"), ASSETS / "plugin-icon.png")
    save(image.convert("RGB"), WIKI / "plugin-icon.png")
    save(mark(1024), ASSETS / "logo-mark.png")


def server_card(draw, x, y, name, players, status, active=False):
    fill = SURFACE if not active else (17, 39, 58)
    outline = LINE if not active else CYAN
    draw.rounded_rectangle((x, y, x + 270, y + 68), radius=15, fill=fill, outline=outline, width=2)
    dot = GREEN if status == "healthy" else MUTED
    draw.ellipse((x + 20, y + 25, x + 32, y + 37), fill=dot)
    draw.text((x + 48, y + 15), name, font=font(18, "semibold"), fill=WHITE)
    draw.text((x + 48, y + 39), status, font=font(13), fill=MUTED)
    draw.text((x + 244, y + 34), players, font=font(17, "semibold"), fill=CYAN_SOFT, anchor="rm")


def hero_banner():
    image = canvas((1600, 500))
    draw = ImageDraw.Draw(image)
    draw.rectangle((0, 0, 8, 500), fill=CYAN)
    image.alpha_composite(mark(170), (58, 134))
    draw.text((250, 101), "VELOCITYNAVIGATOR", font=font(18, "bold"), fill=CYAN)
    draw.text((250, 137), "Lobby routing,", font=font(54, "bold"), fill=WHITE)
    draw.text((250, 198), "without the guesswork.", font=font(54, "bold"), fill=WHITE)
    draw.text((250, 284), "Health-aware balancing for Velocity networks.", font=font(25), fill=MUTED)
    draw.text((250, 364), "v4.3.0", font=font(16, "semibold"), fill=CYAN_SOFT)
    draw.text((370, 364), "Velocity 3.x", font=font(16, "semibold"), fill=MUTED)
    draw.text((490, 364), "Java 17+", font=font(16, "semibold"), fill=MUTED)
    draw.text((580, 364), "Universal JAR", font=font(16, "semibold"), fill=MUTED)
    draw.line((1135, 92, 1135, 408), fill=LINE, width=2)
    draw.text((1195, 64), "LIVE ROUTING", font=font(14, "bold"), fill=MUTED)
    draw.rounded_rectangle((1184, 91, 1500, 417), radius=22, fill=(9, 22, 39), outline=LINE, width=2)
    server_card(draw, 1207, 120, "lobby-01", "42 / 100", "healthy", True)
    server_card(draw, 1207, 202, "lobby-02", "67 / 100", "healthy")
    server_card(draw, 1207, 284, "lobby-03", "31 / 100", "healthy")
    draw.text((1342, 386), "selected: lobby-01", font=font(14, "semibold"), fill=CYAN, anchor="mm")
    draw.text((1530, 462), "DemonZ Development", font=font(14), fill=(87, 106, 126), anchor="rs")
    save(image.convert("RGB"), ASSETS / "hero-banner.png")
    save(image.convert("RGB"), WIKI / "hero-banner.png")


WIKI_HEADER_SPECS = {
    "advanced-proxy-systems.png": ("NETWORK FEATURES", "Parties, Queues & Redis", "Optional tools for networks that need more than basic lobby routing."),
    "algorithm-visualizations.png": ("ROUTING", "See the Algorithms in Action", "Simple examples of how each routing mode spreads players."),
    "backend-lifecycle-states.png": ("ROUTING CONTROL", "Backend Lifecycle States", "Route only to servers that are ready to receive players."),
    "backend-bridge-configuration.png": ("PLAYER MENUS", "Backend Bridge Setup", "Add the optional Paper or Spigot side of the universal JAR."),
    "capacity-queue.png": ("PLAYER FLOW", "Capacity Queue", "Keep joins orderly when every lobby has reached its limit."),
    "commands-and-permissions.png": ("REFERENCE", "Commands & Permissions", "Every player, operator, party, queue, and managed-server command."),
    "configuration-guide.png": ("CONFIGURATION", "Configure It Your Way", "A practical reference for every VelocityNavigator config file."),
    "contextual-routing-guide.png": ("ROUTING", "Keep Game Modes Together", "Send players back to the lobby group that matches their game."),
    "faq.png": ("HELP", "Frequently Asked Questions", "Short answers to the questions server owners ask most often."),
    "feature-overview.png": ("OVERVIEW", "What VelocityNavigator Can Do", "Routing, selectors, resilience, and optional network features."),
    "health-and-circuit-breakers.png": ("RESILIENCE", "Health & Circuit Breakers", "Avoid failed backends and bring them back after recovery."),
    "html-dashboard.png": ("OPERATIONS", "HTML Dashboard", "See live routing, health, players, and network status in a browser."),
    "initial-join-balancing.png": ("ROUTING", "Balance Players from Login", "Choose a healthy lobby before the player reaches a backend."),
    "java-and-bedrock-selectors.png": ("PLAYER MENUS", "Java & Bedrock Selectors", "Offer inventory, form, and chat ways to choose a lobby."),
    "language-packs.png": ("CUSTOMIZATION", "Language Packs", "Use an included language or write messages for your community."),
    "migration-guide.png": ("UPGRADING", "Moving from v3 to v4", "Keep your existing setup while adopting the new configuration."),
    "operations-runbook.png": ("OPERATIONS", "Running the Network", "Everyday commands for health checks, maintenance, and recovery."),
    "party-system.png": ("PLAYER FEATURES", "Party System", "Keep friends together as their leader moves across the network."),
    "player-affinity.png": ("ROUTING", "Player Affinity", "Prefer a player's recent healthy lobby without giving up balancing."),
    "prometheus-grafana-setup.png": ("MONITORING", "Prometheus & Grafana", "Turn routing and health data into useful dashboards."),
    "quick-start-guide.png": ("GETTING STARTED", "Your First Balanced Lobby", "A small working setup you can build in a few minutes."),
    "redis-and-multi-proxy.png": ("MULTI-PROXY", "Redis Synchronization", "Share routing state and backend registration across proxies."),
    "retries-and-fallbacks.png": ("RECOVERY", "Retries & Fallbacks", "Choose what happens when a route or connection cannot complete."),
    "routing-algorithms.png": ("ROUTING", "Choose the Right Strategy", "Pick a routing mode that matches the way your network runs."),
    "server-management.png": ("OPERATIONS", "Server Management", "Safely add, inspect, and remove Velocity servers from one command."),
    "storage-and-databases.png": ("DATA", "Storage & Databases", "Understand local files, saved affinity, and Redis-backed sharing."),
    "troubleshooting-guide.png": ("HELP", "Fix Common Problems", "Practical checks for routing, menus, Redis, and configuration."),
}


def wiki_headers():
    for filename, (eyebrow, title, subtitle) in WIKI_HEADER_SPECS.items():
        image = canvas((1400, 260))
        draw = ImageDraw.Draw(image)
        draw.rectangle((0, 0, 8, 260), fill=CYAN)
        draw.text((58, 34), eyebrow, font=font(14, "bold"), fill=CYAN)
        draw.text((58, 70), title, font=font(46, "bold"), fill=WHITE)
        draw.text((58, 139), subtitle, font=font(20), fill=MUTED)
        draw.rounded_rectangle((58, 197, 151, 229), radius=16, fill=(13, 42, 52))
        draw.text((104, 213), "v4.3", font=font(12, "bold"), fill=CYAN_SOFT, anchor="mm")
        draw.rounded_rectangle((165, 197, 283, 229), radius=16, fill=SURFACE, outline=LINE, width=1)
        draw.text((224, 213), "USER GUIDE", font=font(11, "bold"), fill=MUTED, anchor="mm")
        image.alpha_composite(mark(190), (1160, 34))
        draw.line((1097, 38, 1097, 222), fill=LINE, width=2)
        save(image.convert("RGB"), WIKI_HEADERS / filename)


def panel_base():
    image = canvas((1200, 420))
    draw = ImageDraw.Draw(image)
    draw.line((607, 54, 607, 366), fill=LINE, width=2)
    return image, draw


def panel_heading(draw, eyebrow, title, subtitle):
    draw.text((64, 55), eyebrow.upper(), font=font(15, "bold"), fill=CYAN)
    draw.text((64, 94), title, font=font(42, "bold"), fill=WHITE)
    draw.text((64, 160), subtitle, font=font(21), fill=MUTED)


def panel_note(draw, value):
    draw.ellipse((64, 312, 74, 322), fill=CYAN)
    draw.text((88, 303), value, font=font(15, "semibold"), fill=CYAN_SOFT)


def mini_server(draw, x, y, name, load, selected=False):
    outline = CYAN if selected else LINE
    draw.rounded_rectangle((x, y, x + 218, y + 60), radius=13, fill=SURFACE, outline=outline, width=2)
    draw.ellipse((x + 17, y + 24, x + 27, y + 34), fill=GREEN)
    draw.text((x + 40, y + 13), name, font=font(16, "semibold"), fill=WHITE)
    draw.text((x + 195, y + 30), load, font=font(14, "semibold"), fill=CYAN_SOFT, anchor="rm")


def routing_panel():
    image, draw = panel_base()
    panel_heading(draw, "Smart routing", "Pick the right lobby.", "Eight algorithms, health-aware filtering,\nand persistent affinity.")
    panel_note(draw, "power_of_two is a strong default for most networks")
    px, py = 705, 209
    draw.ellipse((px - 31, py - 31, px + 31, py + 31), fill=SURFACE, outline=CYAN, width=2)
    draw.text((px, py), "P", font=font(18, "bold"), fill=CYAN_SOFT, anchor="mm")
    servers = [(875, 72, "lobby-01", "42%", True), (875, 180, "lobby-02", "67%", False), (875, 288, "lobby-03", "31%", False)]
    for x, y, name, load, selected in servers:
        draw.line((px + 34, py, x - 20, y + 30), fill=CYAN if selected else LINE, width=3 if selected else 2)
        mini_server(draw, x, y, name, load, selected)
    save(image.convert("RGB"), MARKETPLACE / "01-smart-routing.png")


def runtime_card(draw, x, heading, lines, accent):
    draw.rounded_rectangle((x, 92, x + 218, 332), radius=19, fill=SURFACE, outline=accent, width=2)
    draw.rectangle((x, 92, x + 218, 98), fill=accent)
    draw.text((x + 22, 122), heading, font=font(18, "bold"), fill=WHITE)
    for index, value in enumerate(lines):
        y = 175 + index * 44
        draw.ellipse((x + 23, y + 6, x + 31, y + 14), fill=accent)
        draw.text((x + 44, y), value, font=font(15), fill=MUTED)


def universal_panel():
    image, draw = panel_base()
    panel_heading(draw, "Universal deployment", "One JAR. Two runtimes.", "Routing lives on Velocity. The backend\nbridge is installed only when you need it.")
    panel_note(draw, "startup logs clearly identify proxy or backend mode")
    runtime_card(draw, 653, "VELOCITY PROXY", ["routing engine", "health and queues", "admin commands"], CYAN)
    runtime_card(draw, 930, "PAPER / SPIGOT", ["inventory selector", "secure menu bridge", "optional registration"], GREEN)
    draw.rounded_rectangle((855, 184, 949, 240), radius=13, fill=NAVY, outline=CYAN_SOFT, width=2)
    draw.text((902, 212), "same JAR", font=font(14, "bold"), fill=CYAN_SOFT, anchor="mm")
    save(image.convert("RGB"), MARKETPLACE / "02-universal-jar.png")


def state_card(draw, x, heading, description, accent):
    draw.rounded_rectangle((x, 135, x + 148, 252), radius=17, fill=SURFACE, outline=accent, width=2)
    draw.ellipse((x + 18, 20 + 135, x + 30, 32 + 135), fill=accent)
    draw.text((x + 42, 148), heading, font=font(14, "bold"), fill=accent)
    draw.text((x + 18, 193), description, font=font(13), fill=MUTED)


def resilience_panel():
    image, draw = panel_base()
    panel_heading(draw, "Resilience", "Keep failures away from players.", "Circuit breakers, drain mode, retries with\nbackoff, health caches, and fallback pools.")
    panel_note(draw, "unhealthy destinations are filtered before connection")
    state_card(draw, 653, "CLOSED", "healthy\nrouting", GREEN)
    state_card(draw, 831, "OPEN", "traffic\nblocked", (236, 102, 122))
    state_card(draw, 1009, "HALF OPEN", "recovery\nprobe", (240, 182, 83))
    draw.line((676, 310, 735, 310, 754, 278, 784, 341, 813, 294, 843, 310, 1128, 310), fill=CYAN, width=3)
    save(image.convert("RGB"), MARKETPLACE / "04-resilient-routing.png")


def optional_card(draw, x, title, body):
    draw.rounded_rectangle((x, 107, x + 156, 302), radius=17, fill=SURFACE, outline=LINE, width=2)
    draw.text((x + 20, 133), title, font=font(17, "bold"), fill=WHITE)
    draw.text((x + 20, 180), body, font=font(14), fill=MUTED, spacing=7)
    draw.rounded_rectangle((x + 20, 257, x + 108, 284), radius=13, fill=(13, 42, 52))
    draw.text((x + 64, 270), "OPTIONAL", font=font(11, "bold"), fill=GREEN, anchor="mm")


def scale_panel():
    image, draw = panel_base()
    panel_heading(draw, "Advanced systems", "Complexity stays optional.", "Enable parties, capacity queues, or Redis\nonly when your network actually needs them.")
    panel_note(draw, "advanced systems are independently configurable")
    optional_card(draw, 653, "PARTIES", "invite and chat\nleader follow\nlocal proxy scope")
    optional_card(draw, 831, "QUEUE", "capacity aware\naction-bar position\nauto connect")
    optional_card(draw, 1009, "REDIS", "multi-proxy sync\ndynamic register\nTLS and ACL")
    save(image.convert("RGB"), MARKETPLACE / "05-optional-systems.png")


def operations_panel():
    image, draw = panel_base()
    panel_heading(draw, "Operations", "See what routing is doing.", "Health commands, Prometheus metrics, an\noptional dashboard, and safe server management.")
    panel_note(draw, "/vn health and /vn config validate shorten diagnosis")
    draw.rounded_rectangle((652, 75, 1149, 345), radius=20, fill=(9, 22, 39), outline=LINE, width=2)
    draw.text((678, 99), "NETWORK HEALTH", font=font(14, "bold"), fill=CYAN)
    rows = [("lobby-01", "healthy", "42 / 100", GREEN), ("lobby-02", "draining", "67 / 100", (240, 182, 83)), ("lobby-03", "healthy", "31 / 100", GREEN)]
    for index, (name, state, players, accent) in enumerate(rows):
        y = 140 + index * 55
        draw.ellipse((678, y + 7, 688, y + 17), fill=accent)
        draw.text((702, y), name, font=font(15, "semibold"), fill=WHITE)
        draw.text((905, y), state, font=font(14), fill=MUTED)
        draw.text((1120, y), players, font=font(14, "semibold"), fill=CYAN_SOFT, anchor="ra")
        draw.line((678, y + 37, 1120, y + 37), fill=LINE, width=1)
    draw.text((678, 313), "routes/min", font=font(12), fill=MUTED)
    for index, height in enumerate((13, 24, 17, 31, 22, 39, 29, 45, 34)):
        x = 920 + index * 21
        draw.rectangle((x, 331 - height, x + 10, 331), fill=CYAN if index > 5 else LINE)
    save(image.convert("RGB"), MARKETPLACE / "06-operations.png")


def localization_panel():
    image, draw = panel_base()
    panel_heading(draw, "Localization", "Make every message yours.", "Seven built-in languages, custom packs, and\ncolor support across chat and menus.")
    panel_note(draw, "language selection is explicit and never auto-detected")
    codes = ("EN", "RU", "ES", "FR", "DE", "PT-BR", "ZH-CN")
    for index, code in enumerate(codes):
        col = index % 4
        row = index // 4
        x = 652 + col * 121
        y = 100 + row * 72
        draw.rounded_rectangle((x, y, x + 98, y + 48), radius=13, fill=SURFACE, outline=LINE, width=2)
        draw.text((x + 49, y + 24), code, font=font(14, "bold"), fill=CYAN_SOFT, anchor="mm")
    draw.rounded_rectangle((652, 260, 1113, 335), radius=15, fill=(9, 22, 39), outline=LINE, width=2)
    draw.text((675, 277), "messages.toml", font=font(13, "bold"), fill=GREEN)
    draw.text((675, 306), "language = \"custom\"", font=font(15), fill=MUTED)
    save(image.convert("RGB"), MARKETPLACE / "07-localization.png")


def security_panel():
    image, draw = panel_base()
    panel_heading(draw, "Defensive design", "Trust the boundaries.", "Signed registration, one-time menu tokens,\nbounded protocol input, and safer HTTP auth.")
    panel_note(draw, "network-facing systems remain disabled by default")
    draw.rounded_rectangle((660, 85, 1138, 337), radius=22, fill=(9, 22, 39), outline=LINE, width=2)
    items = [("MENU", "single-use token"), ("REDIS", "HMAC registration"), ("HTTP", "bearer header"), ("FILES", "atomic updates")]
    for index, (name, value) in enumerate(items):
        y = 112 + index * 52
        draw.rounded_rectangle((686, y, 774, y + 30), radius=14, fill=(13, 42, 52))
        draw.text((730, y + 15), name, font=font(11, "bold"), fill=GREEN, anchor="mm")
        draw.text((803, y + 5), value, font=font(15, "semibold"), fill=WHITE)
        draw.line((803, y + 36, 1108, y + 36), fill=LINE, width=1)
    save(image.convert("RGB"), MARKETPLACE / "08-defensive-design.png")


def compatibility_panel():
    image, draw = panel_base()
    panel_heading(draw, "Compatibility", "Built for modern Velocity.", "Velocity 3.x and Java 17+, with a bridge\nbuilt on the stable Spigot 1.16.5 API.")
    panel_note(draw, "the backend bridge uses no version-specific NMS")
    draw.text((653, 108), "BACKEND API BASELINE", font=font(14, "bold"), fill=CYAN)
    draw.line((681, 209, 1108, 209), fill=LINE, width=5)
    for x, value in ((681, "PAPER / SPIGOT"), (894, "API 1.16.5+"), (1108, "JAVA 17+")):
        draw.ellipse((x - 10, 199, x + 10, 219), fill=CYAN, outline=CYAN, width=2)
        draw.text((x, 241), value, font=font(14, "semibold"), fill=WHITE, anchor="mm")
    draw.rounded_rectangle((653, 294, 1112, 336), radius=14, fill=SURFACE)
    draw.text((882, 315), "Velocity 3.x   ·   Paper / Spigot   ·   Java 17+", font=font(15, "semibold"), fill=MUTED, anchor="mm")
    save(image.convert("RGB"), MARKETPLACE / "09-compatibility.png")


def footer_panel():
    image = canvas((1200, 260))
    draw = ImageDraw.Draw(image)
    image.alpha_composite(mark(118), (55, 69))
    draw.text((205, 67), "Built for real networks.", font=font(36, "bold"), fill=WHITE)
    draw.text((205, 119), "Open source, fully documented, and configurable by default.", font=font(20), fill=MUTED)
    draw.text((205, 177), "DOCUMENTATION", font=font(13, "bold"), fill=CYAN)
    draw.text((365, 177), "SOURCE", font=font(13, "bold"), fill=CYAN)
    draw.text((455, 177), "SUPPORT", font=font(13, "bold"), fill=CYAN)
    draw.text((1135, 218), "DemonZ Development", font=font(13), fill=(87, 106, 126), anchor="rs")
    save(image.convert("RGB"), MARKETPLACE / "marketplace-footer.png")


def social_post():
    image = canvas((1200, 630))
    draw = ImageDraw.Draw(image)
    image.alpha_composite(mark(260), (850, 168))
    draw.text((72, 83), "VELOCITYNAVIGATOR", font=font(17, "bold"), fill=CYAN)
    draw.text((72, 135), "Lobby routing,", font=font(55, "bold"), fill=WHITE)
    draw.text((72, 201), "without the guesswork.", font=font(55, "bold"), fill=WHITE)
    draw.text((72, 301), "Eight routing algorithms. Health-aware destinations.\nUniversal Java and Bedrock selectors.", font=font(22), fill=MUTED, spacing=12)
    draw.text((72, 454), "v4.3.0", font=font(16, "semibold"), fill=CYAN_SOFT)
    draw.text((190, 454), "Velocity 3.x", font=font(16, "semibold"), fill=MUTED)
    draw.text((310, 454), "Java 17+", font=font(16, "semibold"), fill=MUTED)
    draw.line((72, 535, 1128, 535), fill=LINE, width=2)
    draw.text((72, 559), "DemonZ Development", font=font(14), fill=(87, 106, 126))
    save(image.convert("RGB"), ASSETS / "social-post.png")


def marketplace_listing_copies():
    source = (ASSETS / "modrinth-listing.md").read_text(encoding="utf-8")
    variants = {
        ASSETS / "hangar-listing.md": "Hangar",
        ASSETS / "spigot-resource.md": "Spigot",
    }
    for path, platform in variants.items():
        content = source.replace("this Modrinth page", f"this {platform} page")
        content = content.replace("Use this Modrinth page", f"Use this {platform} page")
        path.write_text(content, encoding="utf-8", newline="\n")


def main():
    plugin_icon()
    hero_banner()
    wiki_headers()
    routing_panel()
    universal_panel()
    resilience_panel()
    scale_panel()
    operations_panel()
    localization_panel()
    security_panel()
    compatibility_panel()
    footer_panel()
    social_post()
    marketplace_listing_copies()


if __name__ == "__main__":
    main()
