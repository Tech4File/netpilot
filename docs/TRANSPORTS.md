# Transports: UDP, TCP, TLS/WSS-CDN — what NetPilot supports and why

## What the engines use today

| Engine | Transport on the wire | Blocked-UDP networks |
|---|---|---|
| WireGuard (embedded) | UDP (default 51820) | UDP-blocked networks need a server-side relay (see below) |
| IKEv2 (platform, 11+) | UDP 500/4500 (ESP over UDP) | same class of limitation, handled by the OS |
| OpenVPN (bridge) | UDP or TCP per the .ovpn (`proto udp`/`tcp`) | TCP-mode .ovpn works where UDP is blocked |

## The "VPN over WebSocket/CDN (SSL/TLS)" idea, honestly

Tools like **wstunnel** wrap WireGuard's UDP packets in
`UDP-in-WebSocket-in-TLS`, so a firewall sees only an HTTPS session (often
fronted by a CDN). It is a real, working pattern — for **your own server**:

- the SERVER runs a small relay (e.g. wstunnel) on 443;
- the CLIENT must run the matching relay locally and point the WireGuard
  `Endpoint` at it (e.g. `127.0.0.1:51820`).

On Android there is **no clean way to run that client relay inside a VPN
app today** (no maintained embeddable wstunnel library); it currently
means a second app or Termux — which violates NetPilot's one-app model.
Embedding a Go relay core is possible in principle and is kept as a
**future investigation**, not a promise. The honest guidance:

1. **Your own server, normal networks** — WireGuard UDP as shipped works
   and is the right default.
2. **Your own server, UDP blocked** — switch your OpenVPN path to a
   **TCP-mode .ovpn** (works today via the engine bridge), or run a
   server-side relay and accept a second app for now.
3. **Public "free VPN" profiles** — NetPilot deliberately does not ship
   or recommend them: you would route ALL your traffic to an unknown
   party. Bring-your-own server is the trust model.

Proxy ecosystems (vmess/vless/trojan over WSS behind CDNs) are censorship-
circumvention proxies, not IP-layer VPNs; embedding those cores would add
large third-party code to the privileged path for a use NetPilot does not
target. Re-evaluated on request, not on the roadmap by default.
