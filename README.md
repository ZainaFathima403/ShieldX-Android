# ShieldX - Android
ShieldX — Cybersecurity platform for phishing detection, scam prevention, and real-time online threat analysis.


## API Configuration

ShieldX uses the following APIs to power its AI assistant and enhance threat detection.

### 1. Groq API — AI Assistant

The Groq API powers the AI Assistant chatbot.

**Setup instructions:**

1. Visit the [Groq Console](https://console.groq.com/keys).
2. Create an account if you don't already have one.
3. Generate an API key and store it securely.
4. Configure the key using the app's local configuration method.

### 2. Google Safe Browsing API — Threat Detection

The Google Safe Browsing API checks scanned URLs against Google's threat database to identify known phishing and malware threats.

**Setup instructions:**

1. Visit the [Google Cloud Console](https://console.cloud.google.com/).
2. Create a Google Cloud project.
3. Navigate to **APIs & Services → Library** and enable the Safe Browsing API.
4. Go to **APIs & Services → Credentials**.
5. Create an API key and store it securely.
6. Configure the key using the app's local configuration method.
