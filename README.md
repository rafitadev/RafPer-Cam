# RafPer Cam

Base Android de câmera avançada com UI premium original, estabilização em camadas e horizon lock real.

## Arquitetura
- `CameraController`: pipeline CameraX (preview/foto/vídeo), foco, zoom, toggles de qualidade e controle de estabilização.
- `HorizonLockEngine`: fusão acelerômetro + giroscópio para estimar roll, suavizar correção e reduzir micro-jitter.
- `CameraScreen`: UI full screen modular com quick controls, indicador de foco, modos e feedback de estabilidade.

## Tuning de qualidade
- `HorizonLockEngine.horizonAlpha`: amortecimento temporal (aumenta suavidade, reduz resposta).
- `HorizonLockEngine.deadZoneDegrees`: filtro para micro oscilações de mão.
- `HorizonLockEngine.maxCorrectionDegrees`: limite de correção angular.
- `CameraController.cycleStabilizationMode`: política OFF/STANDARD/BRUTAL/HORIZON.
- `CameraUiState.minZoom` e `maxZoom`: limites por dispositivo/lente.
- `CameraController.applyCameraQualityControls()`: ponto central para estabilização nativa, torch/flash, zoom e perfis.

## Próximos passos sugeridos
- Perfil dinâmico por aparelho (bitrate/FPS).
- Controle manual opcional de AE/AF lock por longa pressão.
- Persistência de presets de usuário (HDR/AI/ratio/stab/h-lock).
