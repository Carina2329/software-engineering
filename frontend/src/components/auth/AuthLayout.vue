<script setup lang="ts">
defineProps<{
  title: string
  subtitle: string
  eyebrow?: string
}>()
</script>

<template>
  <main class="auth-layout">
    <section class="auth-story" aria-label="平台介绍">
      <div class="brand">
        <span class="brand-mark">竞</span>
        <span>
          <strong>Campus Contest</strong>
          <small>学科竞赛协作平台</small>
        </span>
      </div>

      <div class="story-copy">
        <p class="story-kicker">SECURE ACCESS / 可信入口</p>
        <h1>让每一次参赛，<br />都从可信身份开始。</h1>
        <p class="story-lead">
          统一身份认证、细粒度权限与完整的会话保护，为团队协作和竞赛数据建立清晰的信任边界。
        </p>
      </div>

      <div class="trust-points">
        <div>
          <span>01</span>
          <p><strong>短期凭证</strong>访问令牌仅驻留内存</p>
        </div>
        <div>
          <span>02</span>
          <p><strong>无感续期</strong>安全 Cookie 自动恢复会话</p>
        </div>
        <div>
          <span>03</span>
          <p><strong>权限可见</strong>角色与能力始终有迹可循</p>
        </div>
      </div>
    </section>

    <section class="auth-panel">
      <div class="panel-card">
        <p class="eyebrow">{{ eyebrow || '身份认证' }}</p>
        <h2>{{ title }}</h2>
        <p class="subtitle">{{ subtitle }}</p>
        <slot />
      </div>
      <p class="panel-footnote">Campus Contest · Identity & Access</p>
    </section>
  </main>
</template>

<style scoped>
.auth-layout {
  display: grid;
  grid-template-columns: minmax(0, 1.05fr) minmax(430px, 0.95fr);
  min-height: 100vh;
}

.auth-story {
  position: relative;
  display: flex;
  flex-direction: column;
  justify-content: space-between;
  overflow: hidden;
  padding: clamp(32px, 5vw, 72px);
  color: #f4f5eb;
  background:
    linear-gradient(145deg, rgba(10, 35, 31, 0.96), rgba(10, 31, 28, 0.9)),
    repeating-linear-gradient(
      125deg,
      transparent 0,
      transparent 38px,
      rgba(255, 255, 255, 0.035) 39px,
      transparent 40px
    );
}

.auth-story::before {
  position: absolute;
  right: -18%;
  bottom: 8%;
  width: 55%;
  aspect-ratio: 1;
  border: 1px solid rgba(214, 178, 94, 0.28);
  border-radius: 50%;
  box-shadow:
    0 0 0 70px rgba(214, 178, 94, 0.035),
    0 0 0 140px rgba(214, 178, 94, 0.025);
  content: "";
}

.brand {
  position: relative;
  z-index: 1;
  display: flex;
  align-items: center;
  gap: 14px;
}

.brand-mark {
  display: grid;
  width: 46px;
  height: 46px;
  place-items: center;
  border: 1px solid rgba(255, 255, 255, 0.28);
  border-radius: 12px 12px 12px 3px;
  color: #102923;
  background: #d8ba70;
  font-family: Georgia, "Songti SC", serif;
  font-size: 25px;
  font-weight: 700;
}

.brand strong,
.brand small {
  display: block;
}

.brand strong {
  font-family: Georgia, "Songti SC", serif;
  font-size: 18px;
  letter-spacing: 0.04em;
}

.brand small {
  margin-top: 3px;
  color: rgba(244, 245, 235, 0.62);
  font-size: 12px;
  letter-spacing: 0.14em;
}

.story-copy {
  position: relative;
  z-index: 1;
  max-width: 650px;
  margin: min(10vh, 110px) 0;
}

.story-kicker,
.eyebrow {
  margin: 0 0 18px;
  font-size: 11px;
  font-weight: 700;
  letter-spacing: 0.18em;
  text-transform: uppercase;
}

.story-kicker {
  color: #d8ba70;
}

.story-copy h1 {
  margin: 0;
  font-family: Georgia, "Songti SC", serif;
  font-size: clamp(38px, 5vw, 72px);
  font-weight: 500;
  line-height: 1.13;
  letter-spacing: -0.04em;
}

.story-lead {
  max-width: 520px;
  margin: 28px 0 0;
  color: rgba(244, 245, 235, 0.66);
  font-size: 15px;
  line-height: 1.9;
}

.trust-points {
  position: relative;
  z-index: 1;
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  border-top: 1px solid rgba(255, 255, 255, 0.13);
}

.trust-points div {
  padding: 20px 18px 0 0;
}

.trust-points div + div {
  padding-left: 18px;
  border-left: 1px solid rgba(255, 255, 255, 0.13);
}

.trust-points span {
  color: #d8ba70;
  font-family: Georgia, serif;
  font-size: 13px;
}

.trust-points p {
  margin: 8px 0 0;
  color: rgba(244, 245, 235, 0.58);
  font-size: 12px;
  line-height: 1.65;
}

.trust-points strong {
  display: block;
  color: #f4f5eb;
  font-size: 13px;
  font-weight: 600;
}

.auth-panel {
  display: flex;
  flex-direction: column;
  justify-content: center;
  padding: clamp(26px, 5vw, 76px);
  background:
    radial-gradient(circle at 95% 3%, rgba(216, 186, 112, 0.18), transparent 19rem),
    #f7f8f3;
}

.panel-card {
  width: 100%;
  max-width: 480px;
  margin: 0 auto;
  padding: clamp(24px, 4vw, 46px);
  border: 1px solid rgba(18, 50, 43, 0.1);
  border-radius: 18px 18px 18px 4px;
  background: rgba(255, 255, 255, 0.84);
  box-shadow: 0 24px 70px rgba(19, 47, 41, 0.09);
  backdrop-filter: blur(12px);
}

.eyebrow {
  color: #6d7f77;
}

.panel-card h2 {
  margin: 0;
  color: #132d27;
  font-family: Georgia, "Songti SC", serif;
  font-size: clamp(29px, 3vw, 38px);
  font-weight: 500;
  letter-spacing: -0.03em;
}

.subtitle {
  margin: 12px 0 28px;
  color: #6a7873;
  font-size: 14px;
  line-height: 1.7;
}

.panel-footnote {
  margin: 20px auto 0;
  color: #8a9691;
  font-size: 11px;
  letter-spacing: 0.12em;
}

@media (max-width: 980px) {
  .auth-layout {
    grid-template-columns: 1fr;
  }

  .auth-story {
    min-height: 290px;
    padding: 28px;
  }

  .story-copy {
    margin: 44px 0 0;
  }

  .story-copy h1 {
    font-size: clamp(32px, 8vw, 52px);
  }

  .story-lead,
  .trust-points {
    display: none;
  }

  .auth-panel {
    min-height: calc(100vh - 290px);
    padding: 32px 18px;
  }
}

@media (max-width: 560px) {
  .auth-story {
    min-height: 220px;
  }

  .story-copy {
    margin-top: 32px;
  }

  .story-copy h1 {
    font-size: 34px;
  }

  .story-kicker {
    margin-bottom: 10px;
  }

  .auth-panel {
    min-height: auto;
    padding: 20px 12px 32px;
  }

  .panel-card {
    padding: 24px 18px;
    border-radius: 14px 14px 14px 4px;
  }
}
</style>
