#!/usr/bin/env node

import fs from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), "..");

// The UI uses a deliberately compact, monumental register: lexical verbs work as
// imperatives, while labels are nominal clauses. Values are Middle Egyptian roots
// or transparent compounds, not English phonetic substitution.
const lexicon = {
  a: "", about: "r", able: "rḫ", accept: "šsp", accepting: "šsp", access: "wꜣt",
  account: "ḥsb", accounts: "ḥsbw", acknowledge: "rḫ", action: "jrt", active: "ꜥnḫ",
  activate: "sꜥnḫ", add: "wꜣḥ", added: "wꜣḥ", address: "st-n-ḥsb", admin: "jmj-rꜣ",
  after: "m-ḫt", afterwards: "m-ḫt", again: "m-mꜣw", agreeing: "rdj-jb",
  algorithm: "sḫr-n-jrt", all: "nb", allow: "rdj", allows: "rdj", alpha: "tpj",
  already: "m-ḥꜣt", alternative: "ky", ambiguous: "n-mꜣꜥ", amount: "ꜥšꜣt",
  and: "ḥnꜥ", announcement: "wḏ", announcements: "wḏw", another: "ky", any: "nb",
  anybody: "rmṯ-nb", anymore: "grh", appear: "sḫꜥ", appearance: "qd", application: "sš-ꜥnḫ",
  applications: "sšw-ꜥnḫ", applied: "jr", apply: "jr", applying: "jr", are: "",
  as: "mj", ask: "dbḥ", asset: "jḫt", assets: "jḫt", at: "m", attention: "rdj-jb",
  auth: "smn-rn", authenticate: "smn-rn", authentication: "smn-rn", authorization: "smn-rn",
  available: "wn", back: "r-ḥꜣ", backed: "sš-snnw", backup: "sš-snnw", backups: "sšw-snnw",
  balance: "ḥsb-ḫt", balances: "ḥsbw-ḫt", batch: "dmḏt", be: "", become: "ḫpr",
  because: "n-ntt", been: "", before: "m-ḥꜣt", below: "ḫr", best: "nfr-wrt",
  biometric: "ḫꜣj-n-ẖt", biometry: "ḫꜣj-n-ẖt", block: "ḏbt",
  blockstore: "pr-n-ḏbwt", bond: "ṯs", bonded: "ṯs", bonding: "ṯs", browser: "pr-mꜣꜣ",
  build: "qd", builds: "qd", built: "qd", but: "js", button: "ꜥ", buy: "šp", buying: "šp",
  by: "jn", can: "rḫ", cannot: "n-rḫ", card: "šꜥt", cash: "ḥḏ", chain: "ṯswt",
  change: "ky", changed: "ky", changes: "ky", character: "tjt", characters: "tjtw",
  chat: "ḏdwt", check: "smn", choose: "stp", clipboard: "šꜥt-ḏrt", claim: "šsp",
  claimed: "šsp", close: "ḫtm", closed: "ḫtm", code: "ḫtm", codes: "ḫtmw",
  columbia: "kwlmbj", community: "njwt", compatible: "dmḏ-nfr", compliance: "jrt-mj-hp",
  conditions: "wḏw", confirm: "smn", confirmation: "smn", confirmed: "smn", connect: "dmḏ",
  connected: "dmḏ", connecting: "ḥr-dmḏ", connection: "dmḏt", consent: "rdj-jb",
  contact: "ḏd-n", continue: "šm", continuing: "šm", converts: "pẖr", cooldown: "tr-qbb",
  copied: "sš-mjtt", copy: "sš-mjtt", corresponding: "mjtt", could: "rḫ", countries: "tꜣw",
  create: "jr", created: "jr", creating: "jr", creation: "jrt", creator: "jrjw",
  crucial: "ꜥꜣ", crypto: "sštꜣ", current: "m-tr-pn", currently: "m-tr-pn", curve: "qꜣb",
  custom: "n-mrrt", currencies: "ḥḏw-n-tꜣw", data: "sšw", date: "hrw", day: "hrw",
  days: "hrww", decentralized: "nn-ḥqꜣ-wꜥ", decoded: "wn-ḫtm", decryption: "wn-sštꜣ",
  default: "tpj", delete: "dr", deleting: "dr", demeter: "Demeter", deposit: "rdjt",
  description: "mdw", designed: "qd", desired: "mrrt", details: "mdw-nb", device: "ḫt-n-jrt",
  devs: "qdw", digit: "tjt-n-ḥsb", digital: "n-tjtw-ḥsb", disclaimer: "mdw-n-ḫsf",
  discover: "ḥḥ", discovery: "gm", display: "sḫꜥ", disabled: "n-jr", do: "jr", documents: "šꜥwt",
  done: "tm", down: "sš", download: "jnj-sš", downloaded: "jnj-sš", drive: "Drive",
  each: "wꜥ-nb", economy: "ḥsb-ḫt-n-tꜣ", edit: "kḏ-m-mꜣw", email: "šꜥt-n-pt",
  empty: "šw", encrypt: "sštꜣ", encrypted: "sštꜣ", ended: "tm", enough: "ꜥšꜣ",
  ensure: "smn", enter: "rdj-m-ẖnw", entered: "rdj-m-ẖnw", error: "jsft", estimated: "ḫꜣj",
  exact: "mꜣꜥ", excluded: "ḫsf", existing: "wn", exists: "wn", experience: "rḫt",
  expired: "tm-tr", explanation: "sšm", explore: "ḥḥ", expose: "wn-sštꜣ", export: "rdj-r-rwty",
  extra: "ḥrj", extrinsic: "jrt-r-rwty", failed: "n-ḫpr", farming: "skꜣ-ḫt", farms: "sḫwt",
  faq: "ptrw-ḥnꜥ-wšbw", feature: "jrt", fee: "bꜣkw", fees: "bꜣkw", file: "šꜥt",
  finalized: "tm", find: "gm", fingerprint: "ḫtm-ḏbꜥ", first: "tpj", for: "n", forever: "r-nḥḥ",
  forget: "ḫꜣꜥ", free: "nn-jsw", frequent: "ꜥšꜣ-sp", friends: "ḫnmw", from: "m",
  frontrun: "ḫꜣj-m-ḥꜣt", frozen: "qbb", full: "mḥ", function: "jrt", functions: "jrwt",
  funded: "rdj-ḥḏ", funding: "rdjt-ḥḏ", funds: "ḫtw", gallery: "pr-tjtw", genesis: "tpj",
  get: "šsp", gets: "ḫpr", global: "tꜣ-nb", goes: "šm", goods: "ḫtw-nfrw", governance: "ḥqꜣ",
  growth: "rwd", guess: "ḫmt", has: "", hash: "ḫtm", have: "", he: "ntf", header: "tp",
  here: "m-st-tn", hide: "jmn", highly: "wrt", history: "sšw-n-ḫpr", holder: "nb",
  holders: "nbw", hour: "wnwt", hours: "wnwt", how: "mj", i: "jnk", id: "rn",
  if: "jr", important: "ꜥꜣ", import: "jnj-m-ẖnw", imported: "jnj-m-ẖnw", importing: "jnj-m-ẖnw",
  improved: "sꜣꜣ", in: "m", incentive: "fkꜣw", include: "dmḏ", includes: "dmḏ",
  including: "ḥnꜥ", incorrect: "n-mꜣꜥ", information: "rḫt", input: "rdjt", install: "rdj",
  installed: "rdj", insufficient: "n-ꜥšꜣ", internet: "jꜣdt-n-mdw", interpreted: "rḫ",
  invitation: "jꜣst", invitations: "jꜣswt", invite: "jꜣs", inviting: "jꜣs", is: "", it: "st",
  join: "dmḏ", joined: "dmḏ", jurisdiction: "st-n-hp", keep: "sꜣw", language: "rꜣ",
  large: "ꜥꜣ", latest: "mꜣw", later: "m-ḫt", law: "hp", laws: "hpw", left: "sp",
  length: "ꜣwt", library: "pr-sšw", like: "mry", link: "wꜣt", liquidity: "ḫtw-šmw",
  list: "sšw", little: "nḏs", load: "jnj", loading: "ḥr-jnj", locally: "m-st-tn",
  locked: "ḫtm", log: "ꜥq-rn", longer: "ꜣw", lose: "ḫꜣꜥ", losing: "ḫꜣꜥ", lost: "ḫꜣꜥ",
  low: "ḫr", made: "jr", mainnet: "jꜣdt-ꜥꜣ", maintain: "sꜣw", maintained: "sꜣw",
  make: "jr", maker: "jrjw", manage: "sšm", mandatory: "wḏ", many: "ꜥšꜣ", market: "st-n-swnt",
  markets: "stw-n-swnt", matched: "mjtt", max: "wr", maximum: "wr", may: "rḫ", memorandum: "šꜥt-sḫꜣ",
  min: "nḏs", minimum: "nḏs", minute: "ꜣt", minutes: "ꜣwt", mnemonic: "sḫꜣ",
  mode: "qd", monetary: "n-ḥḏ", more: "ꜥšꜣ", movement: "šmt", must: "wḏ", my: "n-j",
  name: "rn", need: "ḫr", needs: "ḫr", network: "jꜣdt-nt-wꜣwt", networks: "jꜣdt-nt-wꜣwt",
  new: "mꜣw", next: "m-ḫt", no: "nn", non: "n", not: "n", nothing: "nn-ḫt", now: "m-nw",
  number: "ḥsb", of: "n", offline: "nn-dmḏ", on: "m", once: "sp-wꜥ", one: "wꜥ",
  only: "wꜥ", open: "wn", operation: "jrt", options: "stpw", or: "r-pw", order: "sḫr",
  others: "kyw", otherwise: "ky", out: "r-rwty", outcome: "prt", output: "prt", paper: "šꜥt",
  parliament: "qnbt", passphrase: "mdw-sḫꜣ", password: "mdw-sštꜣ", passwords: "mdw-sštꜣw",
  payout: "rdjt", payouts: "rdjwt", pending: "ḥr-wꜣḥ", per: "n", perform: "jr",
  performance: "jrt", phone: "ḫt-ḏd-m-wꜣw", pin: "ḥsb-sštꜣ", placeholder: "st-šwt",
  please: "m-ḥtp", points: "ḥsbw", policy: "hp", pool: "š", pooled: "m-š", pools: "šw",
  portfolio: "dmḏt-ḫtw", position: "st", positive: "nfr", possibility: "ḫprt", prediction: "ḫmt-nt-ḫpr",
  price: "jsw", primary: "tpj", privacy: "sštꜣ", problem: "jsft", proceed: "šm",
  process: "jrt", producers: "qdw", program: "sḫr", progress: "m-ḫpr", project: "kꜣt",
  proposals: "sḫrw", protect: "sꜣw", protection: "sꜣw", protocol: "wḏw-n-jrt",
  provide: "rdj", provided: "rdj", provider: "rdjw", providers: "rdjw", provision: "rdjt",
  qr: "QR", query: "ptr", questions: "ptrw", ratio: "ḥsb-psš", raw: "n-kꜣt", read: "šd",
  ready: "grg", receive: "šsp", received: "šsp", recipient: "šspw", recipients: "šspw",
  recent: "mꜣw", recover: "nḥm", recovery: "nḥm", redeemable: "nḥm", referral: "jꜣst",
  referrals: "jꜣswt", referrer: "jꜣsw", refresh: "smꜣw", rejected: "ḫsf", reload: "wḥm-jnj",
  remain: "sp", remove: "dr", removed: "dr", removing: "dr", reponsibility: "jrt-n-jb",
  request: "dbḥ", requests: "dbḥw", required: "wḏ", requirement: "wḏ", requirements: "wḏw",
  reserved: "sꜣw", residents: "rmṯw", restart: "wḥm-ꜥḥꜥ", restoring: "nḥm", result: "prt",
  results: "prwt", retrieve: "nḥm", retry: "wḥm-jr", revert: "wḥm-r-ḥꜣ", review: "mꜣꜣ-m-mꜣw",
  reward: "fkꜣw", rewards: "fkꜣw", rewrite: "wḥm-sš", risk: "snḏ", risks: "snḏw",
  route: "wꜣt", routing: "sšm-wꜣt", run: "jr", same: "mjtt", safely: "m-sꜣw",
  scan: "šd-tjt", scanner: "ḫt-šd-tjt", search: "ḥḥ", sec: "ꜣt-nḏst", second: "ꜣt-nḏst",
  secondary: "snw", secret: "sštꜣ", secure: "sꜣw", security: "sꜣw", see: "mꜣꜣ",
  seed: "prt", select: "stp", selected: "stp", sell: "rdj-n-jsw", send: "hꜣb",
  sender: "hꜣbw", sending: "hꜣb", sent: "hꜣb", services: "bꜣkw", set: "smn",
  setting: "smn", share: "psš", shares: "psšw", shifting: "pẖr", show: "sḫꜥ",
  shown: "sḫꜥ", shows: "sḫꜥ", sign: "ḫtm", signing: "ḫtm", six: "sjs", size: "ꜣwt",
  skip: "swꜣ", slippage: "šmt-jsw", smart: "sꜣꜣ", so: "n-ntt", sold: "rdj-n-jsw",
  sole: "wꜥ", some: "ḫt", something: "ḫt", soon: "m-ꜥ", sorry: "m-ḥtp",
  source: "st-prt", sources: "st-prwt", space: "st", specified: "pn", stable: "mn",
  stake: "ṯs-ḫt", staked: "ṯs-ḫt", staking: "ṯs-ḫt", standalone: "wꜥ", start: "šꜥ",
  status: "wnn", step: "nmtt", still: "sp", stolen: "jṯꜣ", stopped: "tm", store: "sꜣw",
  stored: "sꜣw", storing: "sꜣw", submission: "rdjt", submitted: "rdj", subscribe: "rdj-rn",
  such: "mj", success: "nfr", succesfully: "nfr", successful: "nfr", successfully: "nfr",
  supply: "rdj", support: "sꜣ", supported: "sꜣ", supervision: "mꜣꜣ", sure: "smn",
  swap: "swnt", swapped: "swnt", swedish: "swjd", switch: "ky", symbol: "tjt", symbols: "tjtw",
  synthetic: "jrj", system: "sḫr", taker: "šspw", terms: "mdw", test: "smn",
  tested: "smn", testnet: "jꜣdt-n-smn", than: "r", thank: "dwꜣ", that: "ntt", the: "",
  them: "sn", then: "m-ḫt", there: "m-st-pf", these: "nn", they: "ntsn", this: "pn",
  through: "ḫr", ticker: "tjt-rn-nḏs", time: "tr", timestamp: "ḫtm-tr", to: "r",
  today: "hrw-pn", token: "jḫt", tokens: "jḫt", too: "wrt", total: "dmḏ",
  touch: "dwn-ḏrt", trade: "swnt", trader: "jrjw-swnt", traditional: "n-ḥꜣt", transaction: "jrt-swnt",
  transactions: "jrwt-swnt", transfer: "hꜣb-ḫt", transfers: "hꜣbw-ḫt", transacting: "jrt-swnt",
  transition: "pẖr", troubleshoot: "dr-jsft", try: "jr", turn: "pẖr", type: "qd",
  unable: "n-rḫ", unbond: "wḫꜣ-ṯs", unbonded: "wḫꜣ-ṯs", unbonding: "wḫꜣ-ṯs",
  under: "ḫr", understand: "rḫ", understanding: "rḫ", unfavorably: "n-nfr",
  unfavorable: "n-nfr", unknown: "n-rḫ", unless: "n-jr", unused: "n-jr", unstake: "wḫꜣ-ṯs-ḫt",
  unstaked: "wḫꜣ-ṯs-ḫt", update: "smꜣw", upgrade: "smꜣw", up: "ḥr", upload: "hꜣb-sš",
  uppercase: "ꜥꜣ", upon: "ḥr", us: "n-n", use: "jr-m", used: "jr-m", user: "jrjw",
  users: "jrjw", using: "jr-m", validate: "smn", value: "jsw", values: "jsww",
  verification: "smn", verify: "smn", version: "qd", vested: "sꜣw", view: "mꜣꜣ",
  visible: "sḫꜥ", voluntary: "n-mrrt-jb", vote: "rdj-jb", votes: "rdjwt-jb", voting: "rdj-jb",
  wallet: "pr-ḥḏ", want: "mrr", warning: "sꜣw", was: "", way: "wꜣt", we: "jnn",
  weak: "nḏs", website: "pr-n-jꜣdt-mdw", welcome: "jj-m-ḥtp", well: "nfr", went: "ḫpr",
  whenever: "tr-nb", where: "st", which: "ntj", who: "ntj", will: "r", window: "tr",
  with: "ḥnꜥ", withdrawn: "jnj-m", withdrawal: "jnj-m", without: "nn", won: "n",
  word: "mdw", words: "mdw", working: "ḥr-jrt", works: "jr", world: "tꜣ", worlds: "tꜣw",
  would: "r", write: "sš", writing: "sš", written: "sš", wrong: "n-mꜣꜥ", yes: "jw",
  yesterday: "sf", yet: "sp", you: "ntk", your: "n-k", yourself: "ḏs-k", zero: "nn"
};

Object.assign(lexicon, {
  acceptance: "šsp", accounted: "ḥsb", activity: "jrwt", activities: "jrwt", additional: "ḥrj", addresses: "stw-n-ḥsb",
  affected: "ḫpr-n", affordable: "jsw-nḏs", aimed: "sḫr-r", also: "ḥnꜥ",
  an: "", annualized: "n-rnpt", anyone: "rmṯ-nb", app: "sš-ꜥnḫ", archive: "pr-sšw",
  assistance: "sꜣ", both: "snw", bridged: "swꜣ-m-ꜥrwt", cancel: "ḫꜣꜥ",
  certain: "ḫt", changing: "ky", checkpoint: "st-smn", clearing: "dr-sšw",
  collateral: "ḫt-sꜣw", combining: "dmḏ", compared: "ḫꜣj-ḥnꜥ",
  congratulations: "ḥsj", counterparty: "ky-n-jrt", cover: "ḫbs", decide: "stp",
  dark: "kkw", deleted: "dr", democratic: "n-qnbt-n-rmṯw", denotes: "sšm", detailed: "mḥ-m-mdw",
  different: "ky", during: "m-tr", earn: "šsp", economic: "n-ḥsb-ḫt",
  ecosystem: "dmḏt-n-ꜥnḫ", enabled: "jr", ensures: "smn", established: "smn",
  euro: "ḥḏ-n-tꜣw-mḥtjw", every: "nb", existential: "n-wnn", exciting: "sḏꜣ-jb",
  expand: "swsḫ", explorer: "mꜣꜣ-sšw", fail: "n-ḫpr", farm: "sḫt",
  favorable: "nfr", fiat: "ḥḏ-n-tꜣ", financing: "rdj-ḥḏ", fix: "dr", follow: "šms",
  following: "ntj-m-ḫt", found: "gm", fulfilled: "mḥ", fully: "r-dr", hard: "nḫt",
  documentation: "šꜥwt", don: "n", identity: "rn-n-ḏt", internal: "m-ẖnw", invalid: "n-mꜣꜥ", investment: "rdjt-ḫt",
  involved: "dmḏ", involves: "dmḏ", issue: "jsft", least: "n-nḏs-r", learn: "rḫ",
  legal: "n-hp", limited: "ṯs", liquid: "šm", login: "ꜥq-rn", logout: "prj-rn",
  long: "ꜣw", loose: "ḫꜣꜥ", lower: "ḫrj", makes: "jr", manual: "n-ḏrt",
  match: "mjtt", me: "n-j", might: "rḫ", moment: "ꜣt", ms: "ms",
  much: "ꜥšꜣ", multiverse: "tꜣw-ptw-ꜥšꜣw", natively: "m-ḏt", node: "sbꜣ-n-jꜣdt",
  nodes: "sbꜣw-n-jꜣdt", occurred: "ḫpr", occured: "ḫpr", official: "mꜣꜥ",
  ok: "nfr", operate: "jr", optional: "n-mrrt", other: "ky", over: "m-trw",
  paid: "ḏbꜣ", pair: "snw", parameters: "wḏw-n-jrt", participating: "dmḏ-m",
  particular: "pn", pay: "rdj", payment: "ḏbꜣ", percentage: "psšt", phrase: "mdw",
  portion: "psšt", possibly: "ḫprt", powered: "jr-m-bꜣ", proceeding: "šm",
  proportional: "mj-psšt", rate: "jsw", reading: "šd", recommend: "sḫr",
  rechecked: "wḥm-smn", registration: "rdjt-rn", registered: "rdj-rn",
  reinstalling: "wḥm-rdj", really: "m-mꜣꜥ", replaced: "rdj-m-st",
  resource: "ḫt", responsibility: "jrt-n-jb", restore: "nḥm", return: "jsw",
  runtime: "tr-n-jrt", safe: "sꜣw", seconds: "ꜣwt-nḏst", settings: "stpw",
  setup: "smn", since: "n-ntt", temporarily: "m-tr-nḏs", unavailable: "n-wn",
  unexpected: "n-ḫmt", utilized: "jr-m", when: "tr"
});

// Wallet, connection-consent, and market copy added after the original catalog.
// Keep these as Egyptian words or transparent compounds so new safety messages
// remain intelligible without falling back to English UI prose.
Object.assign(lexicon, {
  actions: "jrwt", adjust: "kḏ-m-mꜣw", agree: "rdj-jb", alone: "wꜥ",
  amounts: "ꜥšꜣwt", approval: "rdj-jb", approving: "ḥr-rdj-jb",
  authorize: "rdj-wꜣt", avoid: "ḫsf", awaiting: "ḥr-wꜣḥ", axis: "wꜣt",
  belongs: "n", binding: "ṯs", blocked: "ḫsf", bytes: "psšw-n-sšw",
  came: "jj", camera: "ḫt-n-mꜣꜣ", cancellation: "ḫꜣꜥ", checked: "smn",
  checking: "ḥr-smn", clearly: "m-mꜣꜥ", collapsed: "ḫtm",
  complete: "tm", completed: "tm", completely: "r-dr", confirming: "ḥr-smn",
  dated: "n-hrw", decimal: "ḥsb-n-psšt", decline: "ḫsf", declined: "ḫsf",
  demand: "dbḥw", duplicate: "snw-mjtt", enable: "sꜥnḫ", enrolled: "smn",
  entire: "r-dr", evidence: "sšw-n-smn", expanded: "swsḫ", filters: "stpw-n-ḥḥ",
  flow: "nmtt", foreground: "m-mꜣꜣ", fresh: "mꜣw", greater: "wr-r",
  help: "sꜣ", hex: "ḥsb-n-ḫtm", incomplete: "n-mḥ", interpret: "rḫ",
  interrupted: "psš", its: "n-st", key: "ḫtm-sštꜣ", loaded: "jnj",
  lock: "ḫtm", meaning: "sšm", message: "mdw", metadata: "sšw-n-sšw",
  never: "nn-sp", observations: "mꜣꜣw", opened: "wn", opening: "ḥr-wn",
  pairing: "dmḏ-snw", paste: "rdj-m-ẖnw", paused: "wꜣḥ", payload: "sšw-n-jrt",
  places: "stw", preview: "mꜣꜣ-m-ḥꜣt", previous: "m-ḥꜣt", prices: "jsww",
  pricing: "ḫꜣj-jsw", probability: "ḫprt", producing: "ḥr-jr", protected: "sꜣw",
  published: "sḫꜥ", quoted: "ḫꜣj", quote: "ḫꜣj-jsw", range: "ꜣwt",
  readable: "rḫ-šd", recognize: "rḫ", reject: "ḫsf", relay: "wꜣt-n-mdw",
  reopened: "wḥm-wn", reopen: "wḥm-wn", replayed: "wḥm-jr",
  requested: "dbḥ", requesting: "ḥr-dbḥ", reset: "wḥm-smn", resolved: "dr-jsft",
  returned: "rdj-r-ḥꜣ", reverse: "pẖr", reverifying: "ḥr-wḥm-smn",
  reverified: "wḥm-smn", rules: "wḏw", saved: "sꜣw", scope: "wꜣt-n-jrt",
  scopes: "wꜣtw-n-jrt", screen: "mꜣꜣt", separate: "psš", session: "tr-n-dmḏ",
  showing: "sḫꜥ", signature: "ḫtm", signatures: "ḫtmw", signs: "ḫtm",
  strong: "nḫt", supplied: "rdj", technical: "n-jrt", text: "mdw",
  told: "ḏd", traders: "jrjw-swnt", trading: "swnt", trusted: "sꜣw-mꜣꜥ",
  trying: "ḥr-jr", two: "snw", until: "r-tr", upgraded: "smꜣw", valid: "mꜣꜥ",
  verified: "smn", verifying: "ḥr-smn", vertical: "ꜥḥꜥ", waiting: "ḥr-wꜣḥ",
  while: "m-tr"
});

const phoneticNames = {
  akkadian: "ꜣqꜣdj", arab: "ꜥrb", azerbaijani: "ꜣḏrbjꜣn", bashkir: "bšqr",
  chinese: "ṯjn", croatian: "ḫrwꜣt", dutch: "nḏrlnd", egyptian: "kmt",
  english: "jngljš", estonian: "jstwn", filipino: "fjljpjn", finnish: "fjn",
  french: "frns", german: "grmn", hebrew: "ꜥbr", hindi: "hnd", indonesian: "jndwns",
  italian: "jtꜣlj", japanese: "jpwn", khmer: "ḫmr", korean: "kwrj", malay: "mlꜣj",
  norwegian: "nrwj", persian: "prs", portuguese: "prtgj", russian: "rwsj", serbian: "srb",
  colombia: "kwlwmbj", spanish: "spꜣnj", taiwan: "tjwꜣn", thai: "tꜣj", turkish: "trk", ukrainian: "wkrꜣjn",
  vietnamese: "wjtnꜣm"
};

const preserved = new Set([
  "ADAR", "APR", "DeFi", "Demeter", "Drive", "ETH", "Ethereum", "EUR",
  "GitHub", "Google", "IBAN", "Instagram", "JSON", "KUSD", "Kusama", "LP",
  "Medium", "Nexus", "PIN", "Polkadot", "Polkamarkt", "Polkaswap", "PSWAP", "QR", "SMART",
  "SORA", "Substrate", "TBC", "Telegram", "Twitter", "URL", "Wiki", "XOR", "XST",
  "XYK", "YouTube", "Taira", "IrohaConnect", "YES", "@sora_happy", "ms", "v1", "v2", "x", "y", "k", "x*y=k"
]);

const aliases = {
  amp: "and", can_t: "cannot", countries_: "countries", don_t: "not", ll: "will", s: "",
  t: "not", ve: "", won_t: "not", you_ll: "you", "сhinese": "chinese"
};

const missingLexemes = new Map();

const signs = {
  "ꜣ": "𓄿", j: "𓇋", y: "𓇌", "ꜥ": "𓂝", w: "𓅱", b: "𓃀", p: "𓊪", f: "𓆑",
  m: "𓅓", n: "𓈖", r: "𓂋", l: "𓂋", h: "𓉔", "ḥ": "𓎛", "ḫ": "𓐍", "ẖ": "𓄡",
  s: "𓋴", z: "𓊃", "š": "𓈙", q: "𓈎", k: "𓎡", g: "𓎼", t: "𓏏",
  "ṯ": "𓍿", d: "𓂧", "ḏ": "𓆓"
};

const overrides = {
  common_akkadian: "@{rꜣ-n-ꜣqꜣdj-js}",
  common_error_mnemonic_is_not_valid: "@{smn-mdw-sḫꜣ-12-r-pw-24-mdw-nb-mꜣꜥ}",
  common_error_mnemonic_length_error: "@{mdw-sḫꜣ-wḏ-12-r-pw-24-mdw}",
  common_error_search_string_error: "@{ꜣwt-ḥḥ-wḏ-m-3-r-64-tjtw}",
  common_error_seed_is_not_valid: "@{smn-rdjt-64-tjtw-n-ḥsb-ḫtmw}",
  backup_password_requirments: "@{n-nḏs-r-sjs-tjtw}. @{n-sꜣw-wr-rdj-tjtw-ꜥꜣw-ḥsbw-ḥnꜥ-tjtw-n-mdw}",
  backup_password_title: "@{mdw-sštꜣ-sštꜣ-sš-snnw-n} Google. @{rdj-st-r-nḥm-pr-ḥḏ}\\n@{ꜣwt-nḏst-n-mdw-sštꜣ-6-tjtw}",
  pincode_length_info_message: "@{hp-sꜣw-n} SORA @{smꜣw-n-sꜣw-k}. 4 @{tjtw-n} PIN @{n-jr}. @{smn} PIN @{mꜣw-6-tjtw}",
  pincode_length_info_title: "@{smn} PIN @{6-tjtw-ḫtm}",
  export_protection_passphrase_description: "@{m-nmtt-ntj-m-ḫt-mꜣꜣ-k-mdw-sḫꜣ-24-mdw-r-nḥm-ḥsb}",
  recovery_input_raw_seed_hint: "@{prt-n-kꜣt} (64 @{tjtw-n-ḥsb-ḫtmw})",
  claim_contact: "@{jr-wn-ptrw-r-pw-snḏw} %%@{ḏd-n-n}%%",
  claim_contact_us: "%%@{ḏd-n-n}%% @{n-ptrw-r-pw-snḏw}",
  tutorial_many_world: "@{tꜣw-ꜥšꜣw}.\\n@{ḥsb-ḫt-wꜥ}.",
  network_badge_mainnet: "@{jꜣdt-ꜥꜣ}",
  network_badge_testnet: "@{jꜣdt-n-smn}",
  wallet_market_back_markets: "@{r-ḥꜣ} · @{stw-n-swnt}"
};

function encodeEgyptian(transliteration, resourceName) {
  let output = "";
  for (const char of transliteration) {
    if (signs[char]) output += signs[char];
    else if (char === "-" || char === ".") output += char === "." ? "." : "";
    else if (/\s|[0-9%(),:;!?·—/€]/u.test(char)) output += char;
    else throw new Error(`${resourceName}: unsupported transliteration character ${JSON.stringify(char)} in ${transliteration}`);
  }
  return output.replace(/ {2,}/g, " ").trim();
}

function protectedSegments(value, resourceName) {
  return value.replace(/@\{([^}]+)\}/g, (_, egyptian) => encodeEgyptian(egyptian, resourceName));
}

function resolveWord(rawWord, resourceName) {
  if (preserved.has(rawWord)) return rawWord;
  const normalized = rawWord
    .normalize("NFKC")
    .replace(/[’']/g, "_")
    .replace(/\\_?/g, "")
    .toLowerCase();
  const alias = aliases[normalized] ?? normalized;
  if (alias === "") return "";
  if (phoneticNames[alias]) return encodeEgyptian(`rꜣ-n-${phoneticNames[alias]}`, resourceName);
  if (Object.hasOwn(lexicon, alias)) return encodeEgyptian(lexicon[alias], resourceName);
  const resources = missingLexemes.get(rawWord) ?? [];
  resources.push(resourceName);
  missingLexemes.set(rawWord, resources);
  return "�";
}

function translateText(source, resourceName) {
  if (overrides[resourceName]) return protectedSegments(overrides[resourceName], resourceName);

  const normalized = source
    .replace(/&amp;/g, "and")
    .replace(/…/g, "...")
    .replace(/can\\?'t/gi, "cannot")
    .replace(/don\\?'t/gi, "not")
    .replace(/won[’']t/gi, "will not")
    .replace(/you[’']ll/gi, "you will")
    .replace(/you[’']ve/gi, "you have")
    .replace(/isn\\?'t/gi, "is not")
    .replace(/\bit[’']s\b/gi, "it is")
    .replace(/\bSORA[’']s\b/g, "SORA")
    .replace(/market maker[’']s/gi, "market maker")
    .replace(/referrer[’']s/gi, "referrer")
    .replace(/referrals[’']/gi, "referrals")
    .replace(/system[’']s/gi, "system")
    .replace(/user[’']s/gi, "user")
    .replace(/x\*y=k/g, "\uE000x*y=k\uE001");

  const tokenPattern = /\uE000[^\uE001]+\uE001|%(?:\d+\$)?[sd]|%%|\\n|@[A-Za-z0-9_]+|[A-Za-z]+\d+|[A-Za-zÀ-žꜣꜥḫẖḥšṯḏС]+|\d+|[^A-Za-zÀ-žꜣꜥḫẖḥšṯḏС\d%\\@\uE000\uE001]+|%/gu;
  const translated = [...normalized.matchAll(tokenPattern)].map(([token]) => {
    if (token.startsWith("\uE000")) return token.slice(1, -1);
    if (/^%(?:\d+\$)?[sd]$/.test(token) || token === "%%" || token === "\\n" || token.startsWith("@")) return token;
    if (/^\d+$/.test(token)) return token;
    if (/^[A-Za-z]+\d+$/.test(token) || /^[A-Za-zÀ-žꜣꜥḫẖḥšṯḏС]+$/u.test(token)) return resolveWord(token, resourceName);
    return token.replace(/[’'\\]/g, "");
  }).join("");

  return translated
    .replace(/\s+([,.;:!?])/g, "$1")
    .replace(/ {2,}/g, " ")
    .replace(/^\s+|\s+$/g, "");
}

function translateXml(sourcePath, destinationPath) {
  const source = fs.readFileSync(sourcePath, "utf8");
  let currentPlural = null;
  const translated = source.split("\n").map((line) => {
    const pluralStart = line.match(/<plurals name="([^"]+)"/);
    if (pluralStart) currentPlural = pluralStart[1];
    if (line.includes("</plurals>")) {
      currentPlural = null;
      return line;
    }
    const match = line.match(/^(\s*)<(string|item)([^>]*)>([\s\S]*)<\/\2>\s*$/);
    if (!match) return line;
    const [, indent, tag, attributes, value] = match;
    if (/translatable="false"/.test(attributes)) return null;
    const name = tag === "string"
      ? attributes.match(/name="([^"]+)"/)?.[1]
      : `${currentPlural}:${attributes.match(/quantity="([^"]+)"/)?.[1]}`;
    if (!name) throw new Error(`Could not identify resource on line: ${line}`);
    return `${indent}<${tag}${attributes}>${translateText(value, name)}</${tag}>`;
  }).filter((line) => line !== null).join("\n");

  fs.mkdirSync(path.dirname(destinationPath), { recursive: true });
  fs.writeFileSync(destinationPath, translated, "utf8");
}

translateXml(
  path.join(root, "common/src/main/res/values/strings.xml"),
  path.join(root, "common/src/main/res/values-b+egy+Egyp/strings.xml")
);
translateXml(
  path.join(root, "app/src/main/res/values/strings.xml"),
  path.join(root, "app/src/main/res/values-b+egy+Egyp/strings.xml")
);

if (missingLexemes.size) {
  const details = [...missingLexemes]
    .map(([word, resources]) => `${word}: ${[...new Set(resources)].slice(0, 3).join(", ")}`)
    .join("\n");
  throw new Error(`Missing Middle Egyptian lexemes (${missingLexemes.size}):\n${details}`);
}

console.log("Generated Middle Egyptian Unicode-hieroglyph resources for common and app.");
