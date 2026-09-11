#!/usr/bin/env python3
"""Minimaler RCON-Client für den Testserver. Keine externen Abhängigkeiten."""

import select
import socket
import struct
import sys
import time

LOGIN = 3
COMMAND = 2
RESPONSE = 0


class RconError(RuntimeError):
    pass


class Rcon:
    def __init__(self, host="127.0.0.1", port=25575, password="chestmanager-test", timeout=15.0):
        self.sock = socket.create_connection((host, port), timeout=timeout)
        self.sock.settimeout(timeout)
        self.request_id = 0
        self._send(LOGIN, password)
        response_id, kind, _ = self._read()
        if response_id == -1:
            raise RconError("RCON-Anmeldung fehlgeschlagen (falsches Passwort?)")

    def _send(self, kind, body):
        self.request_id += 1
        payload = struct.pack("<ii", self.request_id, kind) + body.encode("utf-8") + b"\x00\x00"
        self.sock.sendall(struct.pack("<i", len(payload)) + payload)

    def _recv_exact(self, count):
        data = b""
        while len(data) < count:
            chunk = self.sock.recv(count - len(data))
            if not chunk:
                raise RconError("Verbindung vom Server geschlossen")
            data += chunk
        return data

    def _read(self):
        length = struct.unpack("<i", self._recv_exact(4))[0]
        payload = self._recv_exact(length)
        response_id, kind = struct.unpack("<ii", payload[:8])
        body = payload[8:-2].decode("utf-8", errors="replace")
        return response_id, kind, body

    def command(self, command):
        """Führt einen Befehl aus und gibt die Antwort zurück.

        Lange Antworten, etwa ein volles Truheninventar, schickt der Server in
        mehreren Paketen. Es wird so lange weitergelesen, bis kurz nichts mehr kommt;
        sonst waeren Zaehlungen stillschweigend unvollstaendig.
        """
        self._send(COMMAND, command)
        _, _, body = self._read()
        parts = [body]
        while True:
            ready, _, _ = select.select([self.sock], [], [], 0.35)
            if not ready:
                break
            try:
                _, _, more = self._read()
            except (RconError, OSError, struct.error):
                break
            if not more:
                break
            parts.append(more)
        return "".join(parts)

    def close(self):
        try:
            self.sock.close()
        except OSError:
            pass


def wait_for_server(host="127.0.0.1", port=25575, password="chestmanager-test",
                    timeout=420.0, interval=5.0):
    """Wartet, bis RCON erreichbar ist."""
    deadline = time.time() + timeout
    last_error = None
    while time.time() < deadline:
        try:
            client = Rcon(host, port, password, timeout=10.0)
            return client
        except (OSError, RconError) as error:
            last_error = error
            time.sleep(interval)
    raise RconError(f"Server nicht erreichbar nach {timeout:.0f}s: {last_error}")


if __name__ == "__main__":
    client = wait_for_server()
    try:
        for raw in sys.argv[1:] or ["list"]:
            print(f"> {raw}")
            print(client.command(raw))
    finally:
        client.close()
