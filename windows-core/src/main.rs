fn main() {
    if let Err(error) = santiya_localai_core_native::main_entry() {
        eprintln!("{error}");
        std::process::exit(1);
    }
}
