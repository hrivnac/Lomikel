# Startup scripts must execute statements, not only expressions.
startup_count = 1
loaded = globals().get('answer', 0) + startup_count
