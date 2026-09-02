<script setup>
import { reactive, ref } from 'vue'
import { Eye, EyeOff, Hospital, LogIn, ShieldCheck } from 'lucide-vue-next'
import loginPharmacyHero from '../assets/login-pharmacy-hero.jpg'

defineProps({
  loading: { type: Boolean, default: false },
  error: { type: String, default: '' }
})

const emit = defineEmits(['submit'])
const form = reactive({ username: '', password: '' })
const showPassword = ref(false)

const submit = () => emit('submit', { ...form })
</script>

<template>
  <main class="login-page">
    <section class="login-card" aria-labelledby="login-title">
      <div class="login-visual">
        <div class="login-brandmark"><Hospital :size="21" /><span>Hospital Pharmacy</span></div>
        <div class="login-copy">
          <span>院内药事工作台</span>
          <h1 id="login-title">医院药品闭环管理系统</h1>
          <p>使用院内账号登录</p>
        </div>
        <img :src="loginPharmacyHero" alt="医院药房药品货架与扫码工作台" />
        <div class="login-trust"><ShieldCheck :size="17" />药品调剂与追溯业务学习环境</div>
      </div>

      <form class="login-form" novalidate @submit.prevent="submit">
        <div class="login-form-heading">
          <span>账号登录</span>
          <h2>进入临床工作台</h2>
          <p>请使用已分配的管理员、药师或护士账号。</p>
        </div>

        <label class="field">
          <span>用户名</span>
          <input
            v-model.trim="form.username"
            name="username"
            aria-label="用户名"
            autocomplete="username"
            placeholder="请输入用户名"
            required
            autofocus
          />
        </label>

        <label class="field">
          <span>密码</span>
          <span class="password-field">
            <input
              v-model="form.password"
              name="password"
              aria-label="密码"
              :type="showPassword ? 'text' : 'password'"
              autocomplete="current-password"
              placeholder="请输入密码"
              required
            />
            <button
              type="button"
              class="password-toggle"
              :aria-label="showPassword ? '隐藏密码' : '显示密码'"
              :title="showPassword ? '隐藏密码' : '显示密码'"
              @click="showPassword = !showPassword"
            >
              <EyeOff v-if="showPassword" :size="18" />
              <Eye v-else :size="18" />
            </button>
          </span>
        </label>

        <p v-if="error" class="form-error" role="alert" aria-live="assertive"><span>{{ error }}</span></p>

        <button class="button primary login-submit" type="submit" :disabled="loading">
          <LogIn :size="18" />{{ loading ? '正在验证账号' : '登录系统' }}
        </button>
      </form>
    </section>
  </main>
</template>
