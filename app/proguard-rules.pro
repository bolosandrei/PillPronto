# Faza 0 — reguli minime; se completeaza la introducerea modelelor ML (LiteRT/ONNX).

# Credential Manager (Google Sign-In, Faza 1.5b) — regula recomandata oficial. Fara efect azi
# (isMinifyEnabled = false), dar corecta de avut de pe acum.
-if class androidx.credentials.CredentialManager
-keep class androidx.credentials.playservices.** {
  *;
}
