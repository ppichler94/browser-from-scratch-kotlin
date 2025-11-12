#! /usr/bin/env node

import express from 'express'
import bodyParser from 'body-parser'
import morgan from 'morgan'

const app = express()
app.use(bodyParser.urlencoded())
app.use(morgan('common'))

app.set('views', './views')
app.set('view engine', 'pug')

// const entries = new Map<string, string>()
const entries: string[] = ['Pavel was here']

app.get('/', (req, res) =>
    res.render('comments', { entries }))
app.get('/comment.js')
app.get('/comment.css')
app.get('/login')

app.post('/', (req, res) => {})
app.post('/add', (req, res) => {
    if (req.body !== undefined && 'guest' in req.body) {
        entries.push(req.body.guest)
    } else {
        console.log('Invalid request')
        console.log(req.body)
    }
    res.render('comments', { entries })
})

app.listen(3000)
