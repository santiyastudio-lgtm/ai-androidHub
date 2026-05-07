use std::collections::BTreeMap;

#[derive(Clone, Debug)]
pub enum JsonValue {
    Null,
    Bool(bool),
    Number(f64),
    String(String),
    Array(Vec<JsonValue>),
    Object(BTreeMap<String, JsonValue>),
}

impl JsonValue {
    pub fn stringify(&self) -> String {
        self.stringify_with_indent(None, 0)
    }

    pub fn stringify_pretty(&self) -> String {
        self.stringify_with_indent(Some(2), 0)
    }

    fn stringify_with_indent(&self, indent: Option<usize>, level: usize) -> String {
        match self {
            JsonValue::Null => "null".to_string(),
            JsonValue::Bool(value) => value.to_string(),
            JsonValue::Number(value) => {
                if value.fract() == 0.0 {
                    format!("{}", *value as i64)
                } else {
                    value.to_string()
                }
            }
            JsonValue::String(value) => format!("\"{}\"", escape_json(value)),
            JsonValue::Array(values) => {
                if values.is_empty() {
                    return "[]".to_string();
                }
                if let Some(step) = indent {
                    let pad = " ".repeat(level + step);
                    let close_pad = " ".repeat(level);
                    let rendered = values
                        .iter()
                        .map(|value| format!("{pad}{}", value.stringify_with_indent(indent, level + step)))
                        .collect::<Vec<_>>()
                        .join(",\n");
                    format!("[\n{rendered}\n{close_pad}]")
                } else {
                    let rendered = values.iter().map(JsonValue::stringify).collect::<Vec<_>>().join(",");
                    format!("[{rendered}]")
                }
            }
            JsonValue::Object(values) => {
                if values.is_empty() {
                    return "{}".to_string();
                }
                if let Some(step) = indent {
                    let pad = " ".repeat(level + step);
                    let close_pad = " ".repeat(level);
                    let rendered = values
                        .iter()
                        .map(|(key, value)| {
                            format!(
                                "{pad}\"{}\": {}",
                                escape_json(key),
                                value.stringify_with_indent(indent, level + step)
                            )
                        })
                        .collect::<Vec<_>>()
                        .join(",\n");
                    format!("{{\n{rendered}\n{close_pad}}}")
                } else {
                    let rendered = values
                        .iter()
                        .map(|(key, value)| format!("\"{}\":{}", escape_json(key), value.stringify()))
                        .collect::<Vec<_>>()
                        .join(",");
                    format!("{{{rendered}}}")
                }
            }
        }
    }

    pub fn as_object(&self) -> Option<&BTreeMap<String, JsonValue>> {
        match self {
            JsonValue::Object(value) => Some(value),
            _ => None,
        }
    }

    pub fn as_array(&self) -> Option<&Vec<JsonValue>> {
        match self {
            JsonValue::Array(value) => Some(value),
            _ => None,
        }
    }

    pub fn as_str(&self) -> Option<&str> {
        match self {
            JsonValue::String(value) => Some(value),
            _ => None,
        }
    }

    pub fn as_bool(&self) -> Option<bool> {
        match self {
            JsonValue::Bool(value) => Some(*value),
            _ => None,
        }
    }

    pub fn as_f64(&self) -> Option<f64> {
        match self {
            JsonValue::Number(value) => Some(*value),
            _ => None,
        }
    }

    pub fn get(&self, key: &str) -> Option<&JsonValue> {
        self.as_object().and_then(|object| object.get(key))
    }

    pub fn get_string(&self, key: &str) -> Option<String> {
        self.get(key).and_then(JsonValue::as_str).map(ToOwned::to_owned)
    }

    pub fn get_bool(&self, key: &str) -> Option<bool> {
        self.get(key).and_then(JsonValue::as_bool)
    }

    pub fn get_f64(&self, key: &str) -> Option<f64> {
        self.get(key).and_then(JsonValue::as_f64)
    }
}

pub fn object(entries: Vec<(&str, JsonValue)>) -> JsonValue {
    let mut map = BTreeMap::new();
    for (key, value) in entries {
        map.insert(key.to_string(), value);
    }
    JsonValue::Object(map)
}

pub fn parse_json(input: &str) -> Result<JsonValue, String> {
    let mut parser = Parser::new(input);
    let value = parser.parse_value()?;
    parser.skip_whitespace();
    if !parser.is_eof() {
        return Err("Unexpected trailing JSON input".to_string());
    }
    Ok(value)
}

fn escape_json(input: &str) -> String {
    let mut escaped = String::with_capacity(input.len());
    for ch in input.chars() {
        match ch {
            '"' => escaped.push_str("\\\""),
            '\\' => escaped.push_str("\\\\"),
            '\n' => escaped.push_str("\\n"),
            '\r' => escaped.push_str("\\r"),
            '\t' => escaped.push_str("\\t"),
            '\u{08}' => escaped.push_str("\\b"),
            '\u{0C}' => escaped.push_str("\\f"),
            value if value.is_control() => escaped.push_str(&format!("\\u{:04x}", value as u32)),
            value => escaped.push(value),
        }
    }
    escaped
}

struct Parser<'a> {
    input: &'a [u8],
    index: usize,
}

impl<'a> Parser<'a> {
    fn new(input: &'a str) -> Self {
        Self {
            input: input.as_bytes(),
            index: 0,
        }
    }

    fn is_eof(&self) -> bool {
        self.index >= self.input.len()
    }

    fn peek(&self) -> Option<u8> {
        self.input.get(self.index).copied()
    }

    fn advance(&mut self) -> Option<u8> {
        let byte = self.peek()?;
        self.index += 1;
        Some(byte)
    }

    fn skip_whitespace(&mut self) {
        while let Some(byte) = self.peek() {
            if matches!(byte, b' ' | b'\n' | b'\r' | b'\t') {
                self.index += 1;
            } else {
                break;
            }
        }
    }

    fn parse_value(&mut self) -> Result<JsonValue, String> {
        self.skip_whitespace();
        match self.peek() {
            Some(b'n') => self.parse_null(),
            Some(b't') | Some(b'f') => self.parse_bool(),
            Some(b'-') | Some(b'0'..=b'9') => self.parse_number(),
            Some(b'"') => self.parse_string().map(JsonValue::String),
            Some(b'[') => self.parse_array(),
            Some(b'{') => self.parse_object(),
            Some(value) => Err(format!("Unexpected JSON token '{}'", value as char)),
            None => Err("Unexpected end of JSON input".to_string()),
        }
    }

    fn parse_null(&mut self) -> Result<JsonValue, String> {
        self.expect_keyword("null")?;
        Ok(JsonValue::Null)
    }

    fn parse_bool(&mut self) -> Result<JsonValue, String> {
        if self.match_keyword("true") {
            Ok(JsonValue::Bool(true))
        } else if self.match_keyword("false") {
            Ok(JsonValue::Bool(false))
        } else {
            Err("Invalid boolean literal".to_string())
        }
    }

    fn parse_number(&mut self) -> Result<JsonValue, String> {
        let start = self.index;
        if self.peek() == Some(b'-') {
            self.index += 1;
        }
        self.consume_digits();
        if self.peek() == Some(b'.') {
            self.index += 1;
            self.consume_digits();
        }
        if matches!(self.peek(), Some(b'e') | Some(b'E')) {
            self.index += 1;
            if matches!(self.peek(), Some(b'+') | Some(b'-')) {
                self.index += 1;
            }
            self.consume_digits();
        }
        let raw = std::str::from_utf8(&self.input[start..self.index]).map_err(|_| "Invalid UTF-8 in number".to_string())?;
        let value = raw.parse::<f64>().map_err(|_| format!("Invalid JSON number '{raw}'"))?;
        Ok(JsonValue::Number(value))
    }

    fn consume_digits(&mut self) {
        while matches!(self.peek(), Some(b'0'..=b'9')) {
            self.index += 1;
        }
    }

    fn parse_string(&mut self) -> Result<String, String> {
        if self.advance() != Some(b'"') {
            return Err("Expected opening quote".to_string());
        }
        let mut output = String::new();
        while let Some(byte) = self.advance() {
            match byte {
                b'"' => return Ok(output),
                b'\\' => {
                    let escaped = self.advance().ok_or_else(|| "Unexpected end of escape sequence".to_string())?;
                    match escaped {
                        b'"' => output.push('"'),
                        b'\\' => output.push('\\'),
                        b'/' => output.push('/'),
                        b'b' => output.push('\u{08}'),
                        b'f' => output.push('\u{0C}'),
                        b'n' => output.push('\n'),
                        b'r' => output.push('\r'),
                        b't' => output.push('\t'),
                        b'u' => {
                            let code = self.read_hex4()?;
                            let chr = char::from_u32(code).ok_or_else(|| "Invalid unicode escape".to_string())?;
                            output.push(chr);
                        }
                        value => return Err(format!("Unsupported escape sequence '\\{}'", value as char)),
                    }
                }
                value => output.push(value as char),
            }
        }
        Err("Unterminated JSON string".to_string())
    }

    fn read_hex4(&mut self) -> Result<u32, String> {
        let start = self.index;
        for _ in 0..4 {
            let byte = self.advance().ok_or_else(|| "Unexpected end of unicode escape".to_string())?;
            if !byte.is_ascii_hexdigit() {
                return Err("Invalid unicode escape".to_string());
            }
        }
        let raw = std::str::from_utf8(&self.input[start..self.index]).map_err(|_| "Invalid unicode escape".to_string())?;
        u32::from_str_radix(raw, 16).map_err(|_| "Invalid unicode escape".to_string())
    }

    fn parse_array(&mut self) -> Result<JsonValue, String> {
        self.advance();
        let mut values = Vec::new();
        loop {
            self.skip_whitespace();
            if self.peek() == Some(b']') {
                self.advance();
                break;
            }
            values.push(self.parse_value()?);
            self.skip_whitespace();
            match self.peek() {
                Some(b',') => {
                    self.advance();
                }
                Some(b']') => {
                    self.advance();
                    break;
                }
                _ => return Err("Expected ',' or ']' in JSON array".to_string()),
            }
        }
        Ok(JsonValue::Array(values))
    }

    fn parse_object(&mut self) -> Result<JsonValue, String> {
        self.advance();
        let mut values = BTreeMap::new();
        loop {
            self.skip_whitespace();
            if self.peek() == Some(b'}') {
                self.advance();
                break;
            }
            let key = self.parse_string()?;
            self.skip_whitespace();
            if self.advance() != Some(b':') {
                return Err("Expected ':' in JSON object".to_string());
            }
            let value = self.parse_value()?;
            values.insert(key, value);
            self.skip_whitespace();
            match self.peek() {
                Some(b',') => {
                    self.advance();
                }
                Some(b'}') => {
                    self.advance();
                    break;
                }
                _ => return Err("Expected ',' or '}' in JSON object".to_string()),
            }
        }
        Ok(JsonValue::Object(values))
    }

    fn expect_keyword(&mut self, keyword: &str) -> Result<(), String> {
        if self.match_keyword(keyword) {
            Ok(())
        } else {
            Err(format!("Expected JSON keyword '{keyword}'"))
        }
    }

    fn match_keyword(&mut self, keyword: &str) -> bool {
        let bytes = keyword.as_bytes();
        if self.input.get(self.index..self.index + bytes.len()) == Some(bytes) {
            self.index += bytes.len();
            true
        } else {
            false
        }
    }
}
