# Brand and Marketplace Assets

Brand artwork, marketplace listings, and wiki headers live under `assets/` and `marketplace/`. The Java and Bedrock selector images are real in-game screenshots, and the dashboard preview is a real runtime capture.

## Brand files

| File | Size | Use |
|---|---:|---|
| `plugin-icon.png` | 1024 × 1024 | Marketplace and wiki icon |
| `logo-mark.png` | 1024 × 1024 | Transparent master mark |
| `hero-banner.png` | 1600 × 500 | README, wiki, and marketplace header |
| `social-post.png` | 1200 × 630 | Release and social preview |
| `java-inventory-selector.png` | 924 × 520 | Java Edition inventory selector screenshot |
| `bedrock-selector.png` | 1024 × 528 | Bedrock selector screenshot |
| `dashboard-preview.png` | 747 × 1096 | Operations dashboard with configured and Redis-discovered lobbies |

The visual system uses a navy background, off-white type, and one cyan accent. Avoid glow, heavy-bloom gradients, stock gaming artwork, or dense paragraphs inside images.

## Marketplace panels

The `assets/marketplace/` directory holds 1200 × 420 PNG section headers and visual summaries.

| File | Alt text |
|---|---|
| `01-smart-routing.png` | Player routed to a healthy lobby |
| `02-universal-jar.png` | Universal JAR architecture for Velocity and feature-dependent backend installs |
| `04-resilient-routing.png` | Circuit breaker and health monitoring states |
| `05-optional-systems.png` | Optional party, queue, and Redis systems |
| `06-operations.png` | Network health and operations dashboard |
| `07-localization.png` | Built-in and custom language support |
| `08-defensive-design.png` | Security boundaries for menus, Redis, HTTP, and files |
| `09-compatibility.png` | Velocity and backend bridge compatibility |
| `marketplace-footer.png` | VelocityNavigator documentation and support footer |

## Wiki headers

Guide header images live in `assets/headers/` and are copied to `wiki/headers/` for GitHub wiki rendering.

## Marketplace listings

Platform listing files live in `marketplace/`:

| File | Platform |
|---|---|
| `modrinth-listing.md` | Modrinth (canonical body) |
| `hangar-listing.md` | Hangar |
| `spigot-resource.md` | Spigot (Markdown) |
| `spigot-resource.bbcode` | Spigot (BBcode) |

Keep `hangar-listing.md` and `spigot-resource.md` aligned with the same feature copy while preserving platform-specific download wording. Marketplace bodies must not link to GitHub releases or use GitHub as a download CTA.

Modrinth requires meaningful image alt text. Spigot guidelines discourage substantive description text inside images only.
