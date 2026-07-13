# Advanced Proxy Systems

![Network features](headers/advanced-proxy-systems.png)

VelocityNavigator works well as a simple lobby balancer, but larger networks often need a little more. The features below are optional, so you can enable only the ones that solve a problem for your network.

## Player features

| Feature | What it does | Guide |
|---|---|---|
| Parties | Keeps friends together when the leader changes server | [Party System](Party-System) |
| Capacity queue | Gives players a place in line when every lobby is full | [Capacity Queue](Capacity-Queue) |
| Java and Bedrock selectors | Lets players choose a lobby through an inventory, form, or chat | [Java and Bedrock Selectors](Java-and-Bedrock-Selectors) |
| Language packs | Changes built-in messages and menu text | [Language Packs](Language-Packs) |
| Player affinity | Prefers a player's recent healthy lobby | [Player Affinity](Player-Affinity) |

## Network and operations features

| Feature | What it does | Guide |
|---|---|---|
| Redis | Shares routing health and affinity between Velocity proxies | [Redis and Multi-Proxy](Redis-and-Multi-Proxy) |
| Server management | Safely adds or removes Velocity servers from the admin command | [Server Management](Server-Management) |
| Backend states | Routes only to backends in an allowed gameplay state | [Backend Lifecycle States](Backend-Lifecycle-States) |
| Health and circuit breakers | Avoids failed backends and brings them back after recovery | [Health Checks and Circuit Breakers](Health-Checks-and-Circuit-Breakers) |
| HTML dashboard | Shows a live browser view of routing and server health | [HTML Dashboard](HTML-Dashboard) |
| Storage | Explains what is saved locally and what Redis shares | [Storage and Databases](Storage-and-Databases) |

## What Redis does not share

Party membership and queue positions stay on the Velocity proxy where they were created. On a network with several proxies, keep party members on the same proxy and use load-balancer affinity for players waiting in a queue.

After enabling any of these systems, run:

```text
/vn config validate
```

The command reports invalid ports, command conflicts, missing holding servers, and other common configuration mistakes.
