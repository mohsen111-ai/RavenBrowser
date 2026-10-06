"""Serves the test pages in this folder to the emulator, which reaches the runner at 10.0.2.2.

/slow answers only after 20 seconds, so the bar's Stop button can be tested while a page is loading.
"""
import http.server
import os
import time

os.chdir(os.path.dirname(os.path.abspath(__file__)))


class Handler(http.server.SimpleHTTPRequestHandler):
    def do_GET(self):
        if self.path.startswith("/slow"):
            time.sleep(20)
            body = b"<!doctype html><title>Slow page</title><h1>Finally here</h1>"
            self.send_response(200)
            self.send_header("Content-Type", "text/html")
            self.send_header("Content-Length", str(len(body)))
            self.end_headers()
            try:
                self.wfile.write(body)
            except OSError:
                pass  # The browser stopped waiting, which is the point.
            return
        super().do_GET()

    def log_message(self, fmt, *args):
        pass


http.server.ThreadingHTTPServer(("0.0.0.0", 8000), Handler).serve_forever()
