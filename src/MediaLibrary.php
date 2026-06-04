<?php

namespace Musicplayer\MediaLibrary;

class MediaLibrary
{
    /**
     * Consulta as faixas de áudio do MediaStore do Android.
     *
     * @return array<int, array<string, mixed>> Lista de faixas com metadados
     */
    public function queryAudio(): array
    {
        if (! function_exists('nativephp_call')) {
            return [];
        }

        $result = nativephp_call('MediaLibrary.QueryAudio', '{}');

        if (! $result) {
            return [];
        }

        $decoded = json_decode($result, true);

        return $decoded['tracks'] ?? [];
    }

    /** Verifica (síncrono) se a permissão de leitura de áudio está concedida. */
    public function checkPermission(): bool
    {
        if (! function_exists('nativephp_call')) {
            return false;
        }

        $result = nativephp_call('MediaLibrary.CheckPermission', '{}');
        $decoded = $result ? json_decode($result, true) : [];

        return (bool) ($decoded['granted'] ?? false);
    }

    /** Dispara o diálogo de permissão de leitura de áudio. */
    public function requestPermission(): void
    {
        if (function_exists('nativephp_call')) {
            nativephp_call('MediaLibrary.RequestPermission', '{}');
        }
    }

    /**
     * Abre o seletor de pastas do Android (SAF). É assíncrono: o resultado chega
     * no frontend como o evento DOM `native-event` com event = "folder:chosen"
     * e payload { uri, name } (ou "folder:cancelled").
     */
    public function pickFolder(): void
    {
        if (function_exists('nativephp_call')) {
            nativephp_call('MediaLibrary.PickFolder', '{}');
        }
    }

    /**
     * Enumera e lê os metadados do áudio dentro de uma pasta SAF (tree URI).
     *
     * @return array<int, array<string, mixed>>
     */
    public function scanTree(string $treeUri): array
    {
        if (! function_exists('nativephp_call')) {
            return [];
        }

        $result = nativephp_call('MediaLibrary.ScanTree', json_encode(['uri' => $treeUri]));
        $decoded = $result ? json_decode($result, true) : [];

        return $decoded['tracks'] ?? [];
    }

    /**
     * Verifica se o plugin nativo está carregado.
     */
    public function getStatus(): ?array
    {
        if (! function_exists('nativephp_call')) {
            return null;
        }

        $result = nativephp_call('MediaLibrary.GetStatus', '{}');

        return $result ? json_decode($result, true) : null;
    }
}
